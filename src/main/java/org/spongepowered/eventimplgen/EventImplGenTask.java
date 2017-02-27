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

import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.ClassGenerator;
import org.spongepowered.eventimplgen.factory.ClassGeneratorProvider;
import org.spongepowered.eventimplgen.factory.FactoryInterfaceGenerator;
import org.spongepowered.eventimplgen.factory.NullPolicy;
import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.SpoonModelBuilder;
import spoon.compiler.Environment;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.declaration.CtAnnotationImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class EventImplGenTask extends SourceTask {

    private static final String EVENT_CLASS_PROCESSOR = EventInterfaceProcessor.class.getCanonicalName();
    private Factory factory;
    private PropertySorter sorter;

    private File output;
    private String outputFactory;

    private String sortPriorityPrefix = "";
    private Map<String, String> groupingPrefixes = Collections.emptyMap();
    private boolean validateCode = false;

    @OutputFile
    public File getOutput() {
        return this.output;
    }

    public void setOutput(Object output) {
        setOutput(getProject().file(output));
    }

    public void setOutput(File output) {
        this.output = output;
    }

    @Input
    public String getOutputFactory() {
        return outputFactory;
    }

    public void setOutputFactory(String outputFactory) {
        this.outputFactory = outputFactory;
    }

    @Input
    public String getSortPriorityPrefix() {
        return sortPriorityPrefix;
    }

    public void setSortPriorityPrefix(String sortPriorityPrefix) {
        this.sortPriorityPrefix = sortPriorityPrefix;
    }

    @Input
    public Map<String, String> getGroupingPrefixes() {
        return groupingPrefixes;
    }

    public void setGroupingPrefixes(Map<String, String> groupingPrefixes) {
        this.groupingPrefixes = groupingPrefixes;
    }

    @Input
    public boolean isValidateCode() {
        return validateCode;
    }

    public void setValidateCode(boolean validateCode) {
        this.validateCode = validateCode;
    }

    @TaskAction
    public void generateClasses() throws IOException {
        // Initialize spoon
        SpoonAPI spoon = new Launcher();
        spoon.addProcessor(EVENT_CLASS_PROCESSOR);

        final Environment environment = spoon.getEnvironment();
        environment.setComplianceLevel(8);
        environment.setAutoImports(true);

        // Configure AST generator
        spoon.getEnvironment().setNoClasspath(!this.validateCode);
        final SourceSet sourceSet =
            getProject().getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final SpoonModelBuilder compiler = spoon.createCompiler();
        compiler.setSourceClasspath(toPathArray(this.output.getAbsolutePath(), sourceSet.getCompileClasspath()));
        sourceSet.getAllJava().getSrcDirs().forEach(compiler::addInputSource);
        this.factory = compiler.getFactory();
        // Generate AST
        compiler.build();
        // Analyse AST
        final EventInterfaceProcessor processor = new EventInterfaceProcessor(getSource());
        compiler.process(Collections.singletonList(processor));
        final Map<CtType<?>, List<Property>> foundProperties = processor.getFoundProperties();

        this.sorter = new PropertySorter(this.sortPriorityPrefix, this.groupingPrefixes);

        dumpClasses(foundProperties);
    }

    private void dumpClasses(Map<CtType<?>, List<Property>> foundProperties) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(this.output.toPath()))) {
            String packageName = this.outputFactory.substring(0, this.outputFactory.lastIndexOf('.'));
            ClassGeneratorProvider provider = new ClassGeneratorProvider(packageName);
            byte[] clazz = FactoryInterfaceGenerator.createClass(this.outputFactory, foundProperties, provider, this.sorter);
            addClass(jar, this.outputFactory, clazz);

            ClassGenerator generator = new ClassGenerator();
            generator.setNullPolicy(NullPolicy.NON_NULL_BY_DEFAULT);

            for (CtType<?> event : foundProperties.keySet()) {
                byte[] implClass = generator.createClass(event, ClassGenerator.getEventName(event, provider), getBaseClass(event),
                        foundProperties.get(event), this.sorter, FactoryInterfaceGenerator.plugins);

                this.addClass(jar, ClassGenerator.getEventName(event, provider), implClass);
            }
        }
    }

    private void addClass(JarOutputStream jar, String name, byte[] clazz) throws IOException {
        JarEntry entry = new JarEntry(name.replace('.', '/') + ".class");
        jar.putNextEntry(entry);
        jar.write(clazz);
        jar.closeEntry();
    }

    private CtTypeReference<?> getBaseClass(CtType<?> event) {
        CtAnnotation<?> implementedBy = null;
        int max = Integer.MIN_VALUE;

        final Queue<CtType<?>> queue = new ArrayDeque<>();

        queue.add(event);
        CtType<?> scannedType;

        while ((scannedType = queue.poll()) != null) {
            CtAnnotation<?> anno = getAnnotation(scannedType, "org.spongepowered.api.util.annotation.eventgen.ImplementedBy");
            if (anno != null && EventImplGenTask.<Integer>getValue(anno, "priority") >= max) {
                implementedBy = anno;
                max = getValue(anno, "priority");
            }

            for (CtTypeReference<?> implInterface : scannedType.getSuperInterfaces()) {
                queue.offer(implInterface.getDeclaration());
            }
        }

        if (implementedBy != null) {
            return implementedBy.<CtFieldRead<?>>getValue("value").getVariable().getDeclaringType();
        }
        return factory.Type().OBJECT;
    }

    public static <T> T getValue(CtAnnotation<?> anno, String key) {
        return ((CtAnnotationImpl<?>) anno).getElementValue(key);
    }

    public static CtAnnotation<?> getAnnotation(CtElement type, String name) {
        for (CtAnnotation<?> annotation: type.getAnnotations()) {
            if (annotation.getAnnotationType().getQualifiedName().equals(name)) {
                return annotation;
            }
        }
        return null;
    }

    public static String generateMethodName(CtType<?> event) {
        final StringBuilder name = new StringBuilder();
        do {
            name.insert(0, event.getSimpleName());
            event = event.getDeclaringType();
        } while (event != null);
        name.insert(0, "create");
        return name.toString();
    }

    private String[] toPathArray(String outputJar, FileCollection fileCollection) {
        final Set<File> files = fileCollection.getFiles();
        final String[] strings = new String[files.size() - 1];
        int i = 0;
        for (File file : files) {
            if (file.getAbsolutePath().equals(outputJar)) {
                continue;
            }
            strings[i++] = file.getAbsolutePath();
        }
        return strings;
    }

}
