/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8146946
 * @summary implement javac -m option
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main MOptionTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import com.sun.source.util.JavacTask;

public class MOptionTest extends ModuleTestBase {
    public static void main(String... args) throws Exception {
        new MOptionTest().runTests();
    }

    @Test
    void testOneModule(Path base) throws Exception {
        Path src = base.resolve("src");
        Path m1 = src.resolve("m1");
        Path build = base.resolve("build");
        Files.createDirectories(build);

        tb.writeJavaFiles(m1,
                "module m1 {}",
                "package test; public class Test {}");

        tb.new JavacTask()
                .options("-m", "m1", "-modulesourcepath", src.toString(), "-d", build.toString())
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();

        Path moduleInfoClass = build.resolve("m1/module-info.class");
        Path testTestClass = build.resolve("m1/test/Test.class");

        FileTime moduleInfoTimeStamp = Files.getLastModifiedTime(moduleInfoClass);
        FileTime testTestTimeStamp = Files.getLastModifiedTime(testTestClass);

        Path moduleInfo = m1.resolve("module-info.java");
        if (moduleInfoTimeStamp.compareTo(Files.getLastModifiedTime(moduleInfo)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }

        Path testTest = m1.resolve("test/Test.java");
        if (testTestTimeStamp.compareTo(Files.getLastModifiedTime(testTest)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }

        Thread.sleep(2000); //timestamps

        tb.new JavacTask()
                .options("-m", "m1", "-modulesourcepath", src.toString(), "-d", build.toString())
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();

        if (!moduleInfoTimeStamp.equals(Files.getLastModifiedTime(moduleInfoClass))) {
            throw new AssertionError("Classfile update!");
        }

        if (!testTestTimeStamp.equals(Files.getLastModifiedTime(testTestClass))) {
            throw new AssertionError("Classfile update!");
        }

        Thread.sleep(2000); //timestamps

        Files.setLastModifiedTime(testTest, FileTime.fromMillis(System.currentTimeMillis()));

        tb.new JavacTask()
                .options("-m", "m1", "-modulesourcepath", src.toString(), "-d", build.toString())
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();

        if (!moduleInfoTimeStamp.equals(Files.getLastModifiedTime(moduleInfoClass))) {
            throw new AssertionError("Classfile update!");
        }

        if (Files.getLastModifiedTime(testTestClass).compareTo(Files.getLastModifiedTime(testTest)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }
    }

    @Test
    void testNoOutputDir(Path base) throws Exception {
        Path src = base.resolve("src");
        Path m1 = src.resolve("m1");
        Path build = base.resolve("build");
        Files.createDirectories(build);

        tb.writeJavaFiles(m1,
                "module m1 {}",
                "package test; public class Test {}");

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics",
                    "-m", "m1",
                    "-modulesourcepath", src.toString())
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("- compiler.err.output.dir.must.be.specified.with.dash.m.option"))
            throw new Exception("expected output not found");
    }

    @Test
    void testNoModuleSourcePath(Path base) throws Exception {
        Path src = base.resolve("src");
        Path m1 = src.resolve("m1");
        Path build = base.resolve("build");
        Files.createDirectories(build);

        tb.writeJavaFiles(m1,
                "module m1 {}",
                "package test; public class Test {}");

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics",
                        "-m", "m1",
                        "-d", build.toString())
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("- compiler.err.modulesourcepath.must.be.specified.with.dash.m.option"))
            throw new Exception("expected output not found");
    }

    @Test
    void testMultiModule(Path base) throws Exception {
        Path src = base.resolve("src");
        Path m1 = src.resolve("m1");
        Path m2 = src.resolve("m2");
        Path build = base.resolve("build");
        Files.createDirectories(build);

        tb.writeJavaFiles(m1,
                "module m1 {}",
                "package p1; public class C1 {}");

        tb.writeJavaFiles(m2,
                "module m2 {}",
                "package p2; public class C2 {}");

        tb.new JavacTask()
                .options("-m", "m1,m2", "-modulesourcepath", src.toString(), "-d", build.toString())
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();

        Path m1ModuleInfoClass = build.resolve("m1/module-info.class");
        Path classC1 = build.resolve("m1/p1/C1.class");

        Path m2ModuleInfoClass = build.resolve("m2/module-info.class");
        Path classC2 = build.resolve("m2/p2/C2.class");

        FileTime m1ModuleInfoTimeStamp = Files.getLastModifiedTime(m1ModuleInfoClass);
        FileTime C1TimeStamp = Files.getLastModifiedTime(classC1);

        FileTime m2ModuleInfoTimeStamp = Files.getLastModifiedTime(m2ModuleInfoClass);
        FileTime C2TimeStamp = Files.getLastModifiedTime(classC2);

        Path m1ModuleInfo = m1.resolve("module-info.java");
        Path m2ModuleInfo = m2.resolve("module-info.java");

        if (m1ModuleInfoTimeStamp.compareTo(Files.getLastModifiedTime(m1ModuleInfo)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }

        if (m2ModuleInfoTimeStamp.compareTo(Files.getLastModifiedTime(m2ModuleInfo)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }

        Path C1Source = m1.resolve("p1/C1.java");
        Path C2Source = m2.resolve("p2/C2.java");

        if (C1TimeStamp.compareTo(Files.getLastModifiedTime(C1Source)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }

        if (C2TimeStamp.compareTo(Files.getLastModifiedTime(C2Source)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }

        Thread.sleep(2000); //timestamps

        tb.new JavacTask()
                .options("-m", "m1,m2", "-modulesourcepath", src.toString(), "-d", build.toString())
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();

        if (!m1ModuleInfoTimeStamp.equals(Files.getLastModifiedTime(m1ModuleInfoClass))) {
            throw new AssertionError("Classfile update!");
        }

        if (!m2ModuleInfoTimeStamp.equals(Files.getLastModifiedTime(m2ModuleInfoClass))) {
            throw new AssertionError("Classfile update!");
        }

        if (!C1TimeStamp.equals(Files.getLastModifiedTime(classC1))) {
            throw new AssertionError("Classfile update!");
        }

        if (!C2TimeStamp.equals(Files.getLastModifiedTime(classC2))) {
            throw new AssertionError("Classfile update!");
        }

        Thread.sleep(2000); //timestamps

        Files.setLastModifiedTime(C1Source, FileTime.fromMillis(System.currentTimeMillis()));
        Files.setLastModifiedTime(C2Source, FileTime.fromMillis(System.currentTimeMillis()));

        tb.new JavacTask()
                .options("-m", "m1,m2", "-modulesourcepath", src.toString(), "-d", build.toString())
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();

        if (!m1ModuleInfoTimeStamp.equals(Files.getLastModifiedTime(m1ModuleInfoClass))) {
            throw new AssertionError("Classfile update!");
        }

        if (!m2ModuleInfoTimeStamp.equals(Files.getLastModifiedTime(m2ModuleInfoClass))) {
            throw new AssertionError("Classfile update!");
        }

        if (Files.getLastModifiedTime(classC1).compareTo(Files.getLastModifiedTime(C1Source)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }

        if (Files.getLastModifiedTime(classC2).compareTo(Files.getLastModifiedTime(C2Source)) < 0) {
            throw new AssertionError("Classfiles too old!");
        }
    }
}
