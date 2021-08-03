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
package org.spongepowered.eventimplgen.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.util.annotation.eventgen.ImplementedBy;
import org.spongepowered.eventimplgen.AnnotationUtils;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.ClassGenerator;
import org.spongepowered.eventimplgen.factory.EventData;
import org.spongepowered.eventimplgen.factory.FactoryInterfaceGenerator;
import org.spongepowered.eventimplgen.factory.NullPolicy;
import org.spongepowered.eventimplgen.factory.plugin.EventFactoryPlugin;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * A consumer of computed event information, that will generate individual
 * implementation classes as well as the overall factory on request.
 */
@Singleton
public class EventImplWriter implements PropertyConsumer {

    private final Filer filer;
    private final Elements elements;
    private final PropertySorter sorter;
    private final Set<EventFactoryPlugin> plugins;
    private final String outputFactory;
    private final FactoryInterfaceGenerator factoryGenerator;
    private final ClassGenerator generator;

    // Cleared on write at the end of each round
    private final Map<TypeElement, EventData> roundFoundProperties;

    // All found properties, for generating the factory at the end
    private final Map<TypeElement, EventData> allFoundProperties;
    private final List<ExecutableElement> forwardedMethods = new ArrayList<>();
    private boolean failed = false;
    // only write out an event factory if some classes were written
    private boolean classesWritten = false;

    @Inject
    EventImplWriter(
        final Filer filer,
        final Elements elements,
        final PropertySorter sorter,
        final Set<EventFactoryPlugin> plugins,
        final EventGenOptions options,
        final FactoryInterfaceGenerator factoryGenerator,
        final ClassGenerator generator
    ) {
        this.filer = filer;
        this.elements = elements;
        this.sorter = sorter;
        this.plugins = plugins;
        this.roundFoundProperties = new TreeMap<>(Comparator.comparing(e -> elements.getBinaryName(e).toString()));
        this.allFoundProperties = new TreeMap<>(Comparator.comparing(e -> elements.getBinaryName(e).toString()));
        this.outputFactory = options.generatedEventFactory();
        this.factoryGenerator = factoryGenerator;
        this.generator = generator;
    }

    @Override
    public void propertyFound(
        final TypeElement event, final List<Property> property, final Set<? extends Element> originating) {
        this.roundFoundProperties.put(event, new EventData(property, originating));
    }

    @Override
    public void forwardedMethods(final List<? extends ExecutableElement> elements) {
        this.forwardedMethods.addAll(elements);
    }

    /**
     * Called when a round fails. We don't generate factory methods
     * or impl classes unless the appropriate information is available.
     */
    void skipRound() {
        this.roundFoundProperties.clear();
    }

    public void dumpRound(final Set<? extends Element> rootElements) throws IOException {
        this.generator.setNullPolicy(NullPolicy.NON_NULL_BY_DEFAULT);
        JavaFile clazz;
        for (final TypeElement event : this.roundFoundProperties.keySet()) {
            final ClassName name = this.generator.qualifiedName(event);
            if (!rootElements.contains(EventImplGenProcessor.topLevelType(event))) { // only generate for rounds containing the appropriate root elements
                continue;
            }
            final TypeElement existing = this.elements.getTypeElement(name.reflectionName());
            if (existing != null && rootElements.contains(existing)) { // already building the destination type
                continue;
            }

            final @Nullable DeclaredType baseClass = this.getBaseClass(event);
            if (baseClass == null) {
                continue; // an error occurred, don't generate
            }
            clazz = this.generator.createClass(event, name, baseClass,
                this.roundFoundProperties.get(event), this.sorter, this.plugins
            );

            if (clazz != null) {
                this.classesWritten = true;
                clazz.writeTo(this.filer);
            } else {
                this.failed = true;
            }
        }

        this.allFoundProperties.putAll(this.roundFoundProperties);
        this.roundFoundProperties.clear();
    }

    public void dumpFinal() throws IOException {
        if (this.failed || !this.classesWritten) {
            return;
        }
        final JavaFile clazz = this.factoryGenerator.createClass(this.outputFactory, this.allFoundProperties, this.sorter, this.forwardedMethods);
        clazz.writeTo(this.filer);
    }

    private @Nullable DeclaredType getBaseClass(final TypeElement event) {
        AnnotationMirror implementedBy = null;
        int max = Integer.MIN_VALUE;

        final Queue<TypeElement> queue = new ArrayDeque<>();

        queue.add(event);
        TypeElement scannedType;

        while ((scannedType = queue.poll()) != null) {
            final AnnotationMirror anno = AnnotationUtils.getAnnotation(scannedType, ImplementedBy.class);
            Integer priority = AnnotationUtils.getValue(anno, "priority");
            if (priority == null) {
                priority = 1;
            }

            if (anno != null && priority >= max) {
                implementedBy = anno;
                max = priority;
            }

            for (final TypeMirror implInterface : scannedType.getInterfaces()) {
                if (implInterface.getKind() != TypeKind.DECLARED) {
                    continue;
                }
                final Element element = ((DeclaredType) implInterface).asElement();
                if (element == null || !element.getKind().isInterface()) {
                    // todo: error
                    continue;
                }
                queue.offer((TypeElement) element);
            }
        }

        if (implementedBy != null) {
            final TypeMirror type = AnnotationUtils.getValue(implementedBy, "value");
            if (type.getKind() == TypeKind.ERROR) {
                return null;
            }
            return ((DeclaredType) type);
        }
        return (DeclaredType) this.elements.getTypeElement("java.lang.Object").asType();
    }
}
