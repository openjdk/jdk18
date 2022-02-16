/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.security;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * Common library for various security test helper functions.
 */
public final class SecurityUtils {

    private static String getCacerts() {
        String sep = File.separator;
        return System.getProperty("java.home") + sep
                + "lib" + sep + "security" + sep + "cacerts";
    }

    /**
     * Returns the cacerts keystore with the configured CA certificates.
     */
    public static KeyStore getCacertsKeyStore() throws Exception {
        File file = new File(getCacerts());
        if (!file.exists()) {
            return null;
        }
        return KeyStore.getInstance(file, (char[])null);
    }

    /**
     * Removes the specified protocols from the jdk.tls.disabledAlgorithms
     * security property.
     */
    public static void removeFromDisabledTlsAlgs(String... protocols) {
        removeFromDisabledAlgs("jdk.tls.disabledAlgorithms",
                               List.<String>of(protocols));
    }

    /**
     * Removes constraints that contain the specified constraint from the
     * specified security property. For example, List.of("SHA1") will remove
     * any constraint containing "SHA1".
     */
    public static void removeFromDisabledAlgs(String prop,
            List<String> constraints) {
        String value = Security.getProperty(prop);
        value = Arrays.stream(value.split(","))
                      .map(s -> s.trim())
                      .filter(s -> constraints.stream()
                          .allMatch(constraint -> !s.contains(constraint)))
                      .collect(Collectors.joining(","));
        Security.setProperty(prop, value);
    }

    /**
     * Removes the specified algorithms from the
     * jdk.xml.dsig.secureValidationPolicy security property. Matches any
     * part of the algorithm URI.
     */
    public static void removeAlgsFromDSigPolicy(String... algs) {
        removeFromDSigPolicy("disallowAlg", List.<String>of(algs));
    }

    private static void removeFromDSigPolicy(String rule, List<String> algs) {
        String value = Security.getProperty("jdk.xml.dsig.secureValidationPolicy");
        value = Arrays.stream(value.split(","))
                      .filter(v -> !v.contains(rule) ||
                              !anyMatch(v, algs))
                      .collect(Collectors.joining(","));
        Security.setProperty("jdk.xml.dsig.secureValidationPolicy", value);
    }

    private static boolean anyMatch(String value, List<String> algs) {
        for (String alg : algs) {
           if (value.contains(alg)) {
               return true;
           }
        }
        return false;
    }

    /**
     * Sign a jar file (overwrite original file)
     *
     * @param src the original jar file name
     * @param ks keystore to use
     * @param storePass the keystore password
     * @param keyPass the key password
     * @param digestAlg digest algorithm
     * @param alias alias to use
     *
     * @throws IOException
     */
    public static OutputAnalyzer signJarFile(String src, String ks,
                                             String storePass, String keyPass, String digestAlg, String alias) throws Throwable {
        return signJarFile(src, src, ks, storePass, keyPass, digestAlg, alias);
    }

    /**
     * Sign a jar file
     *
     * @param src the original jar file name
     * @param dest the new/signed jar file name
     * @param ks keystore to use
     * @param storePass the keystore password
     * @param keyPass the key password
     * @param digestAlg digest algorithm
     * @param alias alias to use
     *
     * @throws Throwable
     */
    public static OutputAnalyzer signJarFile(String src, String dest, String ks,
                                   String storePass, String keyPass, String digestAlg, String alias) throws Throwable {
        List<String> args = new ArrayList<>();
        args.add("-verbose");
        args.add("-signedjar");
        args.add(dest);
        args.add("-keystore");
        args.add(ks);
        args.add("-storepass");
        args.add(storePass);
        args.add("-keypass");
        args.add(keyPass);
        args.add("-digestalg");
        args.add(digestAlg);
        args.add(src);
        args.add(alias);
        return jarsigner(args).shouldHaveExitValue(0);
    }

    private static OutputAnalyzer jarsigner(List<String> extra)
            throws Throwable {
        jdk.test.lib.JDKToolLauncher launcher = jdk.test.lib.JDKToolLauncher.createUsingTestJDK("jarsigner")
                .addVMArg("-Duser.language=en")
                .addVMArg("-Duser.country=US");
        for (String s : extra) {
            if (s.startsWith("-J")) {
                launcher.addVMArg(s.substring(2));
            } else {
                launcher.addToolArg(s);
            }
        }
        return ProcessTools.executeCommand(launcher.getCommand());
    }

    /**
     * Generate a key pair and store in specified keystore
     *
     * @param ks path to keystore
     * @param alias alias to use in new key pair
     * @param storepass storepass
     * @param keypass keypass
     * @param keyAlg key algorithm
     * @param sigAlg signature algorithm
     *
     * @throws Throwable
     */
    public static void genKeyPair(String ks, String alias, String storepass,
                                  String keypass, String keyAlg, String sigAlg) throws Throwable {
        String keytool = jdk.test.lib.JDKToolFinder.getJDKTool("keytool");
        jdk.test.lib.process.ProcessTools.executeCommand(keytool,
                "-J-Duser.language=en",
                "-J-Duser.country=US",
                "-genkeypair",
                "-keyalg", keyAlg,
                "-sigalg", sigAlg,
                "-alias", alias,
                "-keystore", ks,
                "-keypass", keypass,
                "-dname", "cn=sample",
                "-storepass", storepass
        ).shouldHaveExitValue(0);
    }

    private SecurityUtils() {}
}
