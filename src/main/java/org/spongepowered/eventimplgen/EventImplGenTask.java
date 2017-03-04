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

import static com.google.common.base.Preconditions.checkNotNull;

import org.gradle.api.JavaVersion;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.AbstractCompile;
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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class EventImplGenTask extends AbstractCompile {

    private static final String EVENT_CLASS_PROCESSOR = EventInterfaceProcessor.class.getCanonicalName();
    private Factory factory;
    private PropertySorter sorter;

    private String outputFactory;

    private String sortPriorityPrefix = "original";
    private Map<String, String> groupingPrefixes = new HashMap<>();
    private boolean validateCode = false;

    public EventImplGenTask() {
        this.groupingPrefixes.put("from", "to");
    }

    @Override
    @CompileClasspath // Gradle 3.4+
    @Classpath // Gradle 3.2+
    @InputFiles
    public FileCollection getClasspath() {
        return super.getClasspath();
    }

    @Input
    public String getOutputFactory() {
        return outputFactory;
    }

    public void setOutputFactory(String outputFactory) {
        this.outputFactory = checkNotNull(outputFactory, "outputFactory");
    }

    @Input
    public String getSortPriorityPrefix() {
        return sortPriorityPrefix;
    }

    public void setSortPriorityPrefix(String sortPriorityPrefix) {
        this.sortPriorityPrefix = checkNotNull(sortPriorityPrefix, "sortPriorityPrefix");
    }

    @Input
    public Map<String, String> getGroupingPrefixes() {
        return groupingPrefixes;
    }

    public void setGroupingPrefixes(Map<String, String> groupingPrefixes) {
        this.groupingPrefixes = checkNotNull(groupingPrefixes, "groupingPrefixes");
    }

    @Input
    public boolean isValidateCode() {
        return validateCode;
    }

    public void setValidateCode(boolean validateCode) {
        this.validateCode = validateCode;
    }

    @Override
    protected void compile() {
        try {
            generateClasses();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TaskAction
    public void generateClasses() throws IOException {
        // Clean the destination directory
        getProject().delete(getDestinationDir());

        // Initialize spoon
        SpoonAPI spoon = new Launcher();
        spoon.addProcessor(EVENT_CLASS_PROCESSOR);

        final Environment environment = spoon.getEnvironment();
        environment.setComplianceLevel(Integer.parseInt(JavaVersion.toVersion(getSourceCompatibility()).getMajorVersion()));
        environment.setNoClasspath(!this.validateCode);

        // Configure AST generator
        final SpoonModelBuilder compiler = spoon.createCompiler();
        compiler.setSourceClasspath(toPathArray(getClasspath().getFiles()));

        for (Object source : this.source) {
            if (!(source instanceof SourceDirectorySet)) {
                throw new UnsupportedOperationException("Source of type " + source.getClass() + " is not supported.");
            }

            ((SourceDirectorySet) source).getSrcDirs().forEach(compiler::addInputSource);
        }

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
        String packageName = this.outputFactory.substring(0, this.outputFactory.lastIndexOf('.'));
        ClassGeneratorProvider provider = new ClassGeneratorProvider(packageName);

        Path destinationDir = getDestinationDir().toPath();
        // Create package directory
        Files.createDirectories(destinationDir.resolve(packageName.replace('.', File.separatorChar)));

        byte[] clazz = FactoryInterfaceGenerator.createClass(this.outputFactory, foundProperties, provider, this.sorter);
        addClass(destinationDir, this.outputFactory, clazz);

        ClassGenerator generator = new ClassGenerator();
        generator.setNullPolicy(NullPolicy.NON_NULL_BY_DEFAULT);

        for (CtType<?> event : foundProperties.keySet()) {
            String name = ClassGenerator.getEventName(event, provider);
            clazz = generator.createClass(event, name, getBaseClass(event),
                    foundProperties.get(event), this.sorter, FactoryInterfaceGenerator.plugins);
            this.addClass(destinationDir, name, clazz);
        }
    }

    private void addClass(Path destinationDir, String name, byte[] clazz) throws IOException {
        Path classFile = destinationDir.resolve(name.replace('.', File.separatorChar) + ".class");
        Files.write(classFile, clazz, StandardOpenOption.CREATE_NEW);
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
                queue.offer(implInterface.getTypeDeclaration());
            }
        }

        if (implementedBy != null) {
            if (implementedBy.isShadow()) {
                return ShadowSpoon.getAnnotationTypeReference(implementedBy, "value");
            } else {
                return implementedBy.<CtFieldRead<?>>getValue("value").getVariable().getDeclaringType();
            }
        }
        return factory.Type().OBJECT;
    }

    public static <T> T getValue(CtAnnotation<?> anno, String key) {
        if (anno.isShadow()) {
            return ShadowSpoon.getAnnotationValue(anno, key);
        }
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

    private static String[] toPathArray(Set<File> files) {
        String[] result = new String[files.size()];
        int i = 0;
        for (File file : files) {
            result[i++] = file.getAbsolutePath();
        }
        return result;
    }

}
