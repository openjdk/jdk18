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
 * @bug 1111111
 * @summary weakly signed jars test
 * @library /test/lib
 * @build jdk.test.lib.util.JarUtils
 *        jdk.test.lib.security.SecurityUtils
 * @run main/othervm JarWithOneNonDisabledDigestAlg
 */

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jdk.test.lib.security.SecurityUtils;
import jdk.test.lib.util.JarUtils;

public class JarWithOneNonDisabledDigestAlg {

    static final String JARNAME = "signed.jar";
    private static final String KEYSTORE = "keystore.jks";
    private static final String SHA256ALIAS = "SHA256ALIAS";
    private static final String SHA1ALIAS = "SHA1ALIAS";
    private static final String MD5ALIAS = "MD5ALIAS";
    private static final String STOREPASS = "changeit";
    private static final String KEYPASS = "changeit";
    private static final String TESTFILE = "testfile";

    public static void main(String[] args) throws Throwable {
        SecurityUtils.removeFromDisabledAlgs("jdk.jar.disabledAlgorithms", List.of("SHA1"));
        Files.write(Path.of(TESTFILE), "testFile".getBytes());
        JarUtils.createJarFile(Path.of(JARNAME), Path.of("."), Path.of(TESTFILE));
        SecurityUtils.genKeyPair(KEYSTORE, SHA256ALIAS, STOREPASS, KEYPASS, "rsa", "SHA256withRSA");
        SecurityUtils.genKeyPair(KEYSTORE, SHA1ALIAS, STOREPASS, KEYPASS, "rsa", "SHA1withRSA");
        SecurityUtils.genKeyPair(KEYSTORE, MD5ALIAS, STOREPASS, KEYPASS, "rsa", "MD5withRSA");

        SecurityUtils.signJarFile(JARNAME, KEYSTORE, STOREPASS, KEYPASS, "MD5", SHA1ALIAS).shouldHaveExitValue(0);
        SecurityUtils.signJarFile(JARNAME, KEYSTORE, STOREPASS, KEYPASS, "SHA1", SHA1ALIAS).shouldHaveExitValue(0);
        //SecurityUtils.signJarFile(JARNAME, KEYSTORE, STOREPASS, KEYPASS, "SHA-256", SHA1ALIAS).shouldHaveExitValue(0);


        try (JarFile jf = new JarFile(JARNAME, true)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || isSigningRelated(entry.getName())) {
                    continue;
                }
                InputStream is = jf.getInputStream(entry);
                while (is.read() != -1);
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers == null) {
                    throw new Exception("JarEntry " + entry.getName() +
                        " is not signed");
                }
            }
        }
    }

    private static boolean isSigningRelated(String name) {
        name = name.toUpperCase(Locale.ENGLISH);
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        name = name.substring(9);
        if (name.indexOf('/') != -1) {
            return false;
        }
        return name.endsWith(".SF")
            || name.endsWith(".DSA")
            || name.endsWith(".RSA")
            || name.endsWith(".EC")
            || name.equals("MANIFEST.MF");
    }
}

