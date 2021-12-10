/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8278420
 * @requires vm.compiler2.enabled
 * @summary Sinking a store node is not allowed and misses a bail out.
 * @run main/othervm -Xbatch -XX:LoopMaxUnroll=0 -XX:-LoopUnswitching
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestSinkingBadStore::* compiler.loopopts.TestSinkingBadStore
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.loopopts.TestSinkingBadStore::* compiler.loopopts.TestSinkingBadStore
 */

package compiler.loopopts;

public class TestSinkingBadStore {
    static int iArrFld2[];
    int iFld;
    int iArrFld1[];

    public static void main(String[] args) {
        TestSinkingBadStore obj = new TestSinkingBadStore();
        obj.test();
        obj.test2();
    }

    void test() {
        int x = 197, y = 3, innerLimit = 45;
        boolean b = false;
        long lArr[] = new long[10];
        for (int i = 4; i < 500000; i++) { // OSR-entry -> LoadI for innerLimit, unknown value
            // Mem Phi P1

            // (1) Before loop opts: Memory phis P1-P3 in place
            // (2) After loop predication, peeling the inner loop and IGVN we have this structure:
            // Range check predicate P for iArrFld2[j = 1] (not folded because j >= 0)
            // Peeled iteration
            // Inner loop with corrected init value for j after peeling and IGVN:
            // for (j = -1; j > LoadI; j -= 2)
            // (3) The store iArrFld1[1] = 6 is tried to be sunk out of the loop in split-if because
            // it has only outside the loop uses which hits the assertion to forbid store nodes.
            for (int j = 1; j > innerLimit; j -= 2) {
                // Mem Phi P2: Merge iArrFld2[j] = 8 and P1
                if (x == 43) {
                    // Mem output for iArrFld1:
                    // In (1):
                    // - Outside loop: MergeMem for:
                    //                 lArr null check, lArr[6] range check,
                    // - Inside loop:  P3
                    // In (2):
                    // - Outside loop: Phis merging peeled version and inner loop version of this store for:
                    //                 lArr null check, lArr[6] range check,
                    // - Inside loop:  <none>
                    //   P3 was removed because the type of the inner loop phi was updated to <=-1
                    //   in IGVN due to peeling. This type info propagated to the CastII node of the
                    //   address of the iArrFld2[j] = 8 store below whose type was 0..1 before. Since
                    //   -1 is out of this range, the CastII is replaced by top. As a consequence,
                    //   the store iArrFld2[j] = 8 is removed which lets P3 die (no output anymore).
                    iArrFld1[1] = 6;
                    lArr[6] = 5;
                } else {
                    y = 7;
                }
                // Mem Phi P3: Merge iArrFld1[1] = 6 and P2
                iArrFld2[j] = 8; // Invariant -> moved out with range check predicate P
                if (b) {
                    break;
                }
            }
        }
    }

    // Different test with same explanation..
    void test2() {
        int x = 197, y = 3, innerLimit = 45;
        boolean b = false;
        long lArr[] = new long[10];
        for (int i = 4; i < 100000; i++) {
            for (int j = 1; j > innerLimit; j -= 3) {
                switch (x) {
                    case 43:
                        iArrFld1[1] = 5;
                        lArr[2] = 6;
                    case 8:
                        y = 5;
                }
                iArrFld2[j] = iFld;
                if (b) {
                    break;
                }
            }
        }
    }
}
