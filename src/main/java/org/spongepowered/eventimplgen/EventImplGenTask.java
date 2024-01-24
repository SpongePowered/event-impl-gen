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

import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.gradle.api.JavaVersion;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceTask;
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
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class EventImplGenTask extends AbstractCompile {

    private static final String EVENT_CLASS_PROCESSOR = EventInterfaceProcessor.class.getCanonicalName();
    private Factory factory;
    private PropertySorter sorter;

    private String outputFactory;

    private String sortPriorityPrefix = "original";
    private Map<String, String> groupingPrefixes = new HashMap<>();
    private Set<String> inclusiveAnnotations = new LinkedHashSet<>();
    private Set<String> exclusiveAnnotations = new LinkedHashSet<>();
    private boolean validateCode = false;
    private final List<Object> allSource = new ArrayList<>();

    public EventImplGenTask() {
        this.groupingPrefixes.put("from", "to");
    }

    @Override
    @CompileClasspath
    public FileCollection getClasspath() {
        return super.getClasspath();
    }

    @Input
    public String getOutputFactory() {
        return this.outputFactory;
    }

    public void setOutputFactory(final String outputFactory) {
        this.outputFactory = Objects.requireNonNull(outputFactory, "outputFactory");
    }

    @Input
    public String getSortPriorityPrefix() {
        return this.sortPriorityPrefix;
    }

    public void setSortPriorityPrefix(final String sortPriorityPrefix) {
        this.sortPriorityPrefix = Objects.requireNonNull(sortPriorityPrefix, "sortPriorityPrefix");
    }

    @Input
    public Map<String, String> getGroupingPrefixes() {
        return this.groupingPrefixes;
    }

    public void setGroupingPrefixes(final Map<String, String> groupingPrefixes) {
        this.groupingPrefixes = Objects.requireNonNull(groupingPrefixes, "groupingPrefixes");
    }

    @Input
    public boolean isValidateCode() {
        return this.validateCode;
    }

    public void setValidateCode(final boolean validateCode) {
        this.validateCode = validateCode;
    }

    @Input
    public Set<String> getInclusiveAnnotations() {
        return this.inclusiveAnnotations;
    }

    public void setInclusiveAnnotations(final Set<String> annotations) {
        this.inclusiveAnnotations = annotations;
    }

    public void inclusiveAnnotations(final Set<String> annotations) {
        this.inclusiveAnnotations.addAll(annotations);
    }

    public void inclusiveAnnotation(final String annotation) {
        this.inclusiveAnnotations.add(annotation);
    }

    @Input
    public Set<String> getExclusiveAnnotations() {
        return this.exclusiveAnnotations;
    }

    public void setExclusiveAnnotations(final Set<String> annotations) {
        this.exclusiveAnnotations = annotations;
    }

    public void exclusiveAnnotations(final Set<String> annotations) {
        this.exclusiveAnnotations.addAll(annotations);
    }

    public void exclusiveAnnotation(final String annotation) {
        this.exclusiveAnnotations.add(annotation);
    }

    @Override
    public void setSource(final @NonNull FileTree source) {
        this.setSource((Object) source);
    }

    @Override
    public void setSource(final @NonNull Object source) {
        this.allSource.clear();
        this.allSource.add(source);
        super.setSource(source);
    }

    @Override
    public @NonNull SourceTask source(final @NonNull Object@NonNull... sources) {
        Collections.addAll(this.allSource, sources);
        return super.source(sources);
    }

    // @Override // pre-Gradle 6 only
    protected void compile() {
        try {
            this.generateClasses();
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private File destinationDir() {
        if (EventImplGenPlugin.HAS_GRADLE_7) {
            return this.getDestinationDirectory().get().getAsFile();
        } else {
            return this.getDestinationDir();
        }
    }

    @TaskAction
    public void generateClasses() throws IOException {
        // Clean the destination directory
        getProject().delete(this.destinationDir());

        // Initialize spoon
        final SpoonAPI spoon = new Launcher();
        spoon.addProcessor(EventImplGenTask.EVENT_CLASS_PROCESSOR);

        final Environment environment = spoon.getEnvironment();
        environment.setComplianceLevel(Integer.parseInt(JavaVersion.toVersion(getSourceCompatibility()).getMajorVersion()));
        environment.setNoClasspath(!this.validateCode);

        // Configure AST generator
        final SpoonModelBuilder compiler = spoon.createCompiler();
        compiler.setSourceClasspath(EventImplGenTask.toPathArray(getClasspath().getFiles()));

        for (final Object source : this.allSource) {
            if (!(source instanceof SourceDirectorySet)) {
                throw new UnsupportedOperationException("Source of type " + source.getClass() + " is not supported.");
            }

            ((SourceDirectorySet) source).getSrcDirs().forEach(compiler::addInputSource);
        }

        this.factory = compiler.getFactory();

        // Generate AST
        compiler.build();

        // Analyse AST
        final EventInterfaceProcessor processor = new EventInterfaceProcessor(getSource(), this.inclusiveAnnotations, this.exclusiveAnnotations);
        compiler.process(Collections.singletonList(processor));
        final Map<CtType<?>, List<Property>> foundProperties = processor.getFoundProperties();
        final List<CtMethod<?>> forwardedMethods = processor.getForwardedMethods();

        this.sorter = new PropertySorter(this.sortPriorityPrefix, this.groupingPrefixes);

        dumpClasses(foundProperties, forwardedMethods);
    }

    private void dumpClasses(final Map<CtType<?>, List<Property>> foundProperties, final List<CtMethod<?>> forwardedMethods) throws IOException {
        final String packageName = this.outputFactory.substring(0, this.outputFactory.lastIndexOf('.'));
        final ClassGeneratorProvider provider = new ClassGeneratorProvider(packageName);

        final Path destinationDir = this.destinationDir().toPath();
        // Create package directory
        Files.createDirectories(destinationDir.resolve(packageName.replace('.', File.separatorChar)));

        byte[] clazz = FactoryInterfaceGenerator.createClass(this.outputFactory, foundProperties, provider, this.sorter, forwardedMethods);
        addClass(destinationDir, this.outputFactory, clazz);

        final ClassGenerator generator = new ClassGenerator();
        generator.setNullPolicy(NullPolicy.NON_NULL_BY_DEFAULT);
        generator.setTargetCompatibility(JavaVersion.toVersion(this.getTargetCompatibility()));

        for (final CtType<?> event : foundProperties.keySet()) {
            final String name = ClassGenerator.getEventName(event, provider);
            clazz = generator.createClass(event, name, getBaseClass(event),
                    foundProperties.get(event), this.sorter, FactoryInterfaceGenerator.PLUGINS
            );
            this.addClass(destinationDir, name, clazz);
        }
    }

    private void addClass(final Path destinationDir, final String name, final byte[] clazz) throws IOException {
        final Path classFile = destinationDir.resolve(name.replace('.', File.separatorChar) + ".class");
        Files.write(classFile, clazz, StandardOpenOption.CREATE_NEW);
    }

    private CtTypeReference<?> getBaseClass(final CtType<?> event) {
        CtAnnotation<?> implementedBy = null;
        int max = Integer.MIN_VALUE;

        final Queue<CtType<?>> queue = new ArrayDeque<>();

        queue.add(event);
        CtType<?> scannedType;

        while ((scannedType = queue.poll()) != null) {
            final CtAnnotation<?> anno = EventImplGenTask.getAnnotation(scannedType, "org.spongepowered.api.util.annotation.eventgen.ImplementedBy");
            if (anno != null && EventImplGenTask.<Integer>getValue(anno, "priority") >= max) {
                implementedBy = anno;
                max = EventImplGenTask.getValue(anno, "priority");
            }

            for (final CtTypeReference<?> implInterface : scannedType.getSuperInterfaces()) {
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
        return this.factory.Type().OBJECT;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getValue(final CtAnnotation<?> anno, final String key) {
        if (anno.isShadow()) {
            return ShadowSpoon.getAnnotationValue(anno, key);
        }
        final CtExpression<?> expr = anno.getWrappedValue(key);
        if (expr instanceof CtFieldRead) {
            final CtFieldReference<?> fieldRef = ((CtFieldRead<?>) expr).getVariable();
            final Class<?> c = fieldRef.getDeclaringType().getActualClass();
            try {
                final Field f = c.getField(fieldRef.getSimpleName());
                return (T) f.get(null);
            } catch (final Exception e) {
                throw new RuntimeException("Failed to lookup field for ref: " + expr);
            }
        }
        return (T) anno.getValueAsObject(key);
    }

    public static CtAnnotation<?> getAnnotation(final CtElement type, final String name) {
        for (final CtAnnotation<?> annotation : type.getAnnotations()) {
            if (annotation.getAnnotationType().getQualifiedName().equals(name)) {
                return annotation;
            }
        }
        return null;
    }

    public static boolean containsAnnotation(final CtElement type, final Set<String> looking) {
        for (final CtAnnotation<?> annotation : type.getAnnotations()) {
            if (looking.contains(annotation.getAnnotationType().getQualifiedName())) {
                return true;
            }
        }
        return false;
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

    private static String[] toPathArray(final Set<File> files) {
        final String[] result = new String[files.size()];
        int i = 0;
        for (final File file : files) {
            result[i++] = file.getAbsolutePath();
        }
        return result;
    }

}
