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
package org.spongepowered.eventimplgen;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

public class EventImplGenPlugin implements Plugin<Project> {

    private Configuration classpath;

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        final SourceSet mainSourceSet = project.getConvention().getPlugin(JavaPluginConvention.class)
                        .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        final EventImplGenTask task = project.getTasks().create("genEventImpl", EventImplGenTask.class);
        task.source(mainSourceSet.getAllJava());
        task.conventionMapping("destinationDir", () -> project.getLayout().getBuildDirectory().dir("generated/event-factory").get().getAsFile());
        if (GradleVersionUtil.isGradle6(project.getGradle())) {
            task.conventionMapping("destinationDirectory", () -> {
                final DirectoryProperty dir = project.getObjects().directoryProperty();
                dir.set(project.getLayout().getBuildDirectory().dir("generated/event-factory"));
                return dir;
            });
        }
        task.conventionMapping("classpath", () -> this.classpath);

        // Include event factory classes in JAR
        ((CopySpec) project.getTasks().getByName(mainSourceSet.getJarTaskName())).from(task);

        project.afterEvaluate(project1 -> {
            // We add the generated event factory to the compile dependencies.
            // At the same time, we need the other dependencies from the compile
            // configuration available for the Spoon compiler. If we depend on
            // the compile configuration directly, we cause circular task
            // dependencies because our event factory is also included.

            // The easiest way to fix it is creating a copy of the compile configuration
            // before we add the event factory. This needs to be done in afterEvaluate
            // however, so any dependencies added after event-impl-gen is applied will
            // be included in the Spoon classpath.
            this.classpath = project1.getConfigurations().getByName(mainSourceSet.getCompileClasspathConfigurationName()).copyRecursive();

            // Add the event factory to the compile dependencies
            project1.getDependencies().add(mainSourceSet.getCompileConfigurationName(), project.files(task));
        });
    }

}
