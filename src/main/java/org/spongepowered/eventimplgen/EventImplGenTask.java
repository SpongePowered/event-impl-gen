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

import javax.annotation.processing.Filer;
import javax.lang.model.element.ElementKind;
import org.spongepowered.api.util.annotation.eventgen.ImplementedBy;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.ClassGenerator;
import org.spongepowered.eventimplgen.factory.ClassGeneratorProvider;
import org.spongepowered.eventimplgen.factory.FactoryInterfaceGenerator;
import org.spongepowered.eventimplgen.factory.NullPolicy;
import spoon.reflect.factory.Factory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public class EventImplGenTask {

    private static final String EVENT_CLASS_PROCESSOR = EventInterfaceProcessor.class.getCanonicalName();
    private Factory factory;
    private PropertySorter sorter;

    private String outputFactory;

    private String sortPriorityPrefix = "original";
    private Map<String, String> groupingPrefixes = new HashMap<>();
    private Set<String> inclusiveAnnotations = new LinkedHashSet<>();
    private Set<String> exclusiveAnnotations = new LinkedHashSet<>();

    public EventImplGenTask() {
        this.groupingPrefixes.put("from", "to");
    }

    public String getOutputFactory() {
        return this.outputFactory;
    }

    public void setOutputFactory(final String outputFactory) {
        this.outputFactory = Objects.requireNonNull(outputFactory, "outputFactory");
    }

    public String getSortPriorityPrefix() {
        return this.sortPriorityPrefix;
    }

    public void setSortPriorityPrefix(final String sortPriorityPrefix) {
        this.sortPriorityPrefix = Objects.requireNonNull(sortPriorityPrefix, "sortPriorityPrefix");
    }

    public Map<String, String> getGroupingPrefixes() {
        return this.groupingPrefixes;
    }

    public void setGroupingPrefixes(final Map<String, String> groupingPrefixes) {
        this.groupingPrefixes = Objects.requireNonNull(groupingPrefixes, "groupingPrefixes");
    }

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

    public void generateClasses() throws IOException {
        // Analyse AST
        final EventInterfaceProcessor processor = new EventInterfaceProcessor(getSource(), this.inclusiveAnnotations, this.exclusiveAnnotations);
        compiler.process(Collections.singletonList(processor));
        final Map<TypeElement, List<Property>> foundProperties = processor.getFoundProperties();
        final List<ExecutableElement> forwardedMethods = processor.getForwardedMethods();

        this.sorter = new PropertySorter(this.sortPriorityPrefix, this.groupingPrefixes);

        dumpClasses(foundProperties, forwardedMethods);
    }


    public static String generateMethodName(TypeElement event) {
        final StringBuilder name = new StringBuilder();
        do {
            name.insert(0, event.getSimpleName());
            final ElementKind kind = event.getEnclosingElement().getKind();
            event = kind.isClass() || kind.isInterface() ? (TypeElement) event.getEnclosingElement() : null;
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
