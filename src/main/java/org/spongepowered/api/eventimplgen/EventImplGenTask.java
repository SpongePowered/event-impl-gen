/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015-2015 SpongePowered <http://spongepowered.org/>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.api.eventimplgen;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.compiler.Environment;
import spoon.compiler.SpoonCompiler;
import spoon.compiler.SpoonFile;
import spoon.compiler.SpoonFolder;
import spoon.support.compiler.FileSystemFolder;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class EventImplGenTask extends DefaultTask {

    private static final SpoonAPI SPOON = new Launcher();
    private static final String EVENT_CLASS_PROCESSOR = EventClassProcessor.class.getCanonicalName();

    static {
        final Environment environment = SPOON.getEnvironment();
        environment.setAutoImports(true);
        environment.setComplianceLevel(6);
        environment.setGenerateJavadoc(true);
        SPOON.addProcessor(EVENT_CLASS_PROCESSOR);
    }

    @TaskAction
    public void task() {
        final SourceSet sourceSet = getProject().getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main");


        final SpoonCompiler compiler = SPOON.createCompiler();
        compiler.setSourceClasspath(toStringArray(sourceSet.getCompileClasspath()));
        compiler.addInputSource(new File("src/main/java/"));
        compiler.setOutputDirectory(new File("output/"));
        compiler.build();
        compiler.process(Collections.singletonList(EVENT_CLASS_PROCESSOR));
        //compiler.generateProcessedSourceFiles(OutputType.COMPILATION_UNITS);
    }

    private static void addFilesFrom(SpoonCompiler compiler, String folder) {
        final SpoonFolder sourceFolder = new FileSystemFolder(new File(folder));
        for (final SpoonFile sourceFile : sourceFolder.getAllJavaFiles()) {
            if ("package-info.java".equals(sourceFile.getName())) {
                continue;
            }
            compiler.addInputSource(sourceFile);
        }
    }

    private static String[] toStringArray(FileCollection fileCollection) {
        final Set<File> files = fileCollection.getFiles();
        final String[] strings = new String[files.size()];
        int i = 0;
        for (File file : files) {
            strings[i++] = file.getAbsolutePath();
        }
        return strings;
    }

}
