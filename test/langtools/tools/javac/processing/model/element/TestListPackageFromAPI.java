/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278930
 * @summary Check that when a package has Elements listed from both from classes and sources,
 *          and then a nested class is completed, it is not first completed from source via
 *          its enclosing class, and then again for itself.
 * @library /tools/javac/lib
 * @modules java.compiler
 *          jdk.compiler
 * @run main TestListPackageFromAPI
 */

import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.PackageElement;
import javax.tools.DiagnosticListener;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public class TestListPackageFromAPI {

    public static void main(String... args) throws IOException, URISyntaxException, InterruptedException {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(src);
        Files.createDirectories(classes);

        Files.newOutputStream(classes.resolve("Test$Nested1.class")).close();
        Files.newOutputStream(classes.resolve("Test.class")).close();

        Thread.sleep(2000);

        Path testSource = src.resolve("Test.java");

        try (Writer w = Files.newBufferedWriter(testSource)) {
            w.write("""
                    public class Test {
                        public static class Nested {}
                    }
                    """);
        }
        try (JavaFileManager fm = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
            List<JavaFileObject> testClasses = List.of(
                new TestFileObject("Test"),
                new TestFileObject("Test$Nested")
            );
            List<JavaFileObject> testSources = List.of(
                    new TestFileObject("Test",
                                       """
                                       public class Test {
                                           public static class Nested1 {}
                                       }
                                       """)
            );
            JavaFileManager testFM = new ForwardingJavaFileManagerImpl(fm, testClasses, testSources);
            DiagnosticListener<JavaFileObject> noErrors = d -> { throw new AssertionError("Should not happen: " + d); };
            JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, testFM, noErrors, List.of("--class-path", classes.toString(), "-sourcepath", src.toString()), null, List.of(new TestFileObject("Input", "")));
            PackageElement pack = task.getElements().getPackageElement("");
            pack.getEnclosedElements().forEach(e -> System.err.println(e));
        }
    }

    private static class TestFileObject extends SimpleJavaFileObject {

        private final String className;
        private final String code;

        public TestFileObject(String className) throws URISyntaxException {
            super(new URI("mem://" + className + ".class"), Kind.CLASS);
            this.className = className;
            this.code = null;
        }

        public TestFileObject(String className, String code) throws URISyntaxException {
            super(new URI("mem://" + className + ".class"), Kind.SOURCE);
            this.className = className;
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            if (code == null) {
                throw new UnsupportedOperationException();
            }
            return code;
        }

        @Override
        public long getLastModified() {
            return getKind() == Kind.CLASS ? 0 : 1000;
        }

    }

    private static class ForwardingJavaFileManagerImpl extends ForwardingJavaFileManager<JavaFileManager> {

        private final List<JavaFileObject> testClasses;
        private final List<JavaFileObject> testSources;

        public ForwardingJavaFileManagerImpl(JavaFileManager fileManager, List<JavaFileObject> testClasses, List<JavaFileObject> testSources) {
            super(fileManager);
            this.testClasses = testClasses;
            this.testSources = testSources;
        }

        @Override
        public Iterable<JavaFileObject> list(JavaFileManager.Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
            if (location == StandardLocation.CLASS_PATH && packageName.isEmpty()) {
                List<JavaFileObject> result = new ArrayList<>();
                if (kinds.contains(Kind.CLASS)) {
                    result.addAll(testClasses);
                }
                if (kinds.contains(Kind.SOURCE)) {
                    result.addAll(testSources);
                }
                return result;
            }
            return super.list(location, packageName, kinds, recurse);
        }

        @Override
        public String inferBinaryName(JavaFileManager.Location location, JavaFileObject file) {
            if (file instanceof TestFileObject testFO) {
                return testFO.className;
            }
            return super.inferBinaryName(location, file);
        }
    }
}
