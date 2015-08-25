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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.compiler.Environment;
import spoon.compiler.SpoonCompiler;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventImplGenTask extends DefaultTask {

    private static final SpoonAPI SPOON = new Launcher();
    private static final String EVENT_CLASS_PROCESSOR = EventClassProcessor.class.getCanonicalName();

    static {
        SPOON.addProcessor(EVENT_CLASS_PROCESSOR);
        final Environment environment = SPOON.getEnvironment();
        environment.setAutoImports(true);
        environment.setComplianceLevel(6);
        environment.setGenerateJavadoc(true);
    }

    @TaskAction
    public void task() {
        final SourceSet sourceSet =
            getProject().getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final SpoonCompiler compiler = SPOON.createCompiler();
        compiler.setSourceClasspath(toStringArray(sourceSet.getCompileClasspath()));
        for (File sourceFile : sourceSet.getAllJava().getSrcDirs()) {
            compiler.addInputSource(sourceFile);
        }
        compiler.setOutputDirectory(new File("output/"));
        compiler.build();
        compiler.process(Collections.singletonList(EVENT_CLASS_PROCESSOR));

        for (Map.Entry<CtInterface<?>, Map<String, CtTypeReference<?>>> entry : EventClassProcessor.GENERATED_FIELDS.entrySet()) {
            final Map<String, String> constructorSignature = Maps.newLinkedHashMap();
            addToSignature(constructorSignature, entry.getKey(), entry.getValue());
            System.out.println(entry.getKey().getQualifiedName() + "(");
            final int size = constructorSignature.size();
            int i = 0;
            for (Map.Entry<String, String> parameter : constructorSignature.entrySet()) {
                System.out.print("    " + parameter.getValue() + " " + parameter.getKey());
                if (i < size - 1) {
                    System.out.print(',');
                }
                System.out.print('\n');
                i++;
            }
            System.out.println(")");
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

    private static void addToSignature(Map<String, String> signature, CtInterface<?> event, Map<String, CtTypeReference<?>> fields) {
        if (fields == null) {
            return;
        }
        for (CtTypeReference<?> superEventReference : event.getSuperInterfaces()) {
            final CtInterface<?> superEvent = (CtInterface<?>) superEventReference.getDeclaration();
            addToSignature(signature, superEvent, EventClassProcessor.GENERATED_FIELDS.get(superEvent));
        }
        for (Map.Entry<String, CtTypeReference<?>> entry : fields.entrySet()) {
            signature.put(entry.getKey(), toStringWithGeneric(entry.getValue()));
        }
    }

    private static String toStringWithGeneric(CtTypeReference<?> type) {
        String string = type.getQualifiedName();
        final List<CtTypeReference<?>> generics = type.getActualTypeArguments();
        if (!generics.isEmpty()) {
            string += '<' + Joiner.on(", ").join(Lists.transform(generics, TypeToString.INSTANCE)) + '>';
        }
        return string;
    }

    private static class TypeToString implements Function<CtTypeReference<?>, String> {

        private static final TypeToString INSTANCE = new TypeToString();

        @Override
        public String apply(CtTypeReference<?> type) {
            return toStringWithGeneric(type);
        }
    }

}
