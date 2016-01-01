/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
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

import com.google.common.base.Preconditions;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

public class EventImplGenTask extends DefaultTask {

    @TaskAction
    public void task() {
        // Configure AST generator
        final EventImplGenExtension extension = getProject().getExtensions().getByType(EventImplGenExtension.class);
        Preconditions.checkState(!extension.eventImplCreateMethod.isEmpty(), "Gradle property eventImplCreateMethod isn't defined");
        final SourceSet sourceSet =
            getProject().getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final WorkingSource workingSource = new WorkingSource();
        for (File sourceFile : sourceSet.getAllJava().getFiles()) {
            workingSource.add(sourceFile);
        }
        for (String entry : new TreeSet<>(workingSource.getClasses().keySet())) {
            if (extension.isIncluded(entry)) {
                System.out.println(entry);
            }
        }
    }

    private static String camelCaseToWords(String camelCase) {
        final StringBuilder words = new StringBuilder();
        int nextUppercase = camelCase.length();
        for (int i = 1; i < nextUppercase; i++) {
            if (Character.isUpperCase(camelCase.charAt(i))) {
                nextUppercase = i;
                break;
            }
        }
        words.append(Character.toLowerCase(camelCase.charAt(0)));
        words.append(camelCase.substring(1, nextUppercase));
        if (nextUppercase < camelCase.length()) {
            words.append(' ');
            words.append(camelCaseToWords(camelCase.substring(nextUppercase)));
        }
        return words.toString();
    }

    private static String[] toPathArray(FileCollection fileCollection) {
        final Set<File> files = fileCollection.getFiles();
        final String[] strings = new String[files.size()];
        int i = 0;
        for (File file : files) {
            strings[i++] = file.getAbsolutePath();
        }
        return strings;
    }

}
