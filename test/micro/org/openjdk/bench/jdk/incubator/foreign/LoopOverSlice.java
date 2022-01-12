/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.foreign;

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.ValueLayout;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static jdk.incubator.foreign.MemoryLayout.PathElement.sequenceElement;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = { "--add-modules=jdk.incubator.foreign", "--enable-native-access=ALL-UNNAMED" })

public class LoopOverSlice {

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int)JAVA_INT.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;

    MemorySegment nativeSegment, heapSegment;
    ResourceScope scope;

    @Setup
    public void setup() {
        scope = ResourceScope.newConfinedScope();
        nativeSegment = MemorySegment.allocateNative(ALLOC_SIZE, scope);
        heapSegment = MemorySegment.ofArray(new float[ELEM_SIZE]);
    }

    @TearDown
    public void tearDown() {
        scope.close();
    }

    @Benchmark
    public void native_slice_loop() {
        new NativeWrapper(nativeSegment).forEach(NativeWrapper.Element::get);
    }

    @Benchmark
    public void heap_slice_loop() {
        new HeapWrapper(heapSegment).forEach(HeapWrapper.Element::get);
    }

    class HeapWrapper implements Iterable<HeapWrapper.Element> {
        final MemorySegment segment;

        public HeapWrapper(MemorySegment segment) {
            this.segment = segment;
        }

        @Override
        public Iterator<Element> iterator() {
            return new Iterator<Element>() {

                MemorySegment current = segment;

                @Override
                public boolean hasNext() {
                    return current.byteSize() > 4;
                }

                @Override
                public Element next() {
                    Element element = new Element(current);
                    current = current.asSlice(4);
                    return element;
                }
            };
        }

        static class Element {
            final MemorySegment segment;

            public Element(MemorySegment segment) {
                this.segment = segment;
            }

            int get() {
                return segment.getAtIndex(JAVA_INT, 0);
            }
        }
    }

    class NativeWrapper implements Iterable<NativeWrapper.Element> {
        final MemorySegment segment;

        public NativeWrapper(MemorySegment segment) {
            this.segment = segment;
        }

        int get() {
            return segment.getAtIndex(JAVA_INT, 0);
        }

        @Override
        public Iterator<Element> iterator() {
            return new Iterator<Element>() {

                MemorySegment current = segment;

                @Override
                public boolean hasNext() {
                    return current.byteSize() > 4;
                }

                @Override
                public Element next() {
                    Element element = new Element(current);
                    current = current.asSlice(4);
                    return element;
                }
            };
        }

        static class Element {
            final MemorySegment segment;

            public Element(MemorySegment segment) {
                this.segment = segment;
            }

            int get() {
                return segment.getAtIndex(JAVA_INT, 0);
            }
        }
    }
}
