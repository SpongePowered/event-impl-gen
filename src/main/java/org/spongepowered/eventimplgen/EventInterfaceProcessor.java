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

import org.gradle.api.file.FileTree;
import org.spongepowered.eventimplgen.eventgencore.AccessorFirstStrategy;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySearchStrategy;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeInformation;
import spoon.reflect.declaration.ModifierKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class EventInterfaceProcessor extends AbstractProcessor<CtInterface<?>> {

    private static final PropertySearchStrategy SEARCH_STRATEGY = new AccessorFirstStrategy(true);
    private final Map<CtType<?>, List<Property>> foundProperties = new TreeMap<>(Comparator.comparing(CtTypeInformation::getQualifiedName));
    private final List<CtMethod<?>> forwardedMethods = new ArrayList<>();
    private final FileTree source;
    private final Set<String> inclusiveAnnotations;
    private final Set<String> exclusiveAnnotations;

    public EventInterfaceProcessor(final FileTree source, final Set<String> inclusiveAnnotations, final Set<String> exclusiveAnnotations) {
        this.source = source;
        this.inclusiveAnnotations = inclusiveAnnotations;
        this.exclusiveAnnotations = exclusiveAnnotations;
    }

    public Map<CtType<?>, List<Property>> getFoundProperties() {
        return this.foundProperties;
    }

    public List<CtMethod<?>> getForwardedMethods() {
        return this.forwardedMethods;
    }

    @Override
    public boolean isToBeProcessed(final CtInterface<?> candidate) {
        return this.source.contains(candidate.getPosition().getCompilationUnit().getFile()) && this.shouldGenerate(candidate);
    }

    @Override
    public void process(final CtInterface<?> event) {
        this.foundProperties.put(event, EventInterfaceProcessor.SEARCH_STRATEGY.findProperties(event.getReference()));
        this.forwardedMethods.addAll(this.findForwardedMethods(event));
    }

    public boolean shouldGenerate(final CtInterface<?> candidate) {
        if (EventImplGenTask.containsAnnotation(candidate, this.inclusiveAnnotations)) {
            return true;
        }
        if (EventImplGenTask.containsAnnotation(candidate, this.exclusiveAnnotations)) {
            return false;
        }
        return candidate.getNestedTypes().isEmpty();
    }

    private List<CtMethod<?>> findForwardedMethods(final CtInterface<?> event) {
        final List<CtMethod<?>> methods = new ArrayList<>();
        for (final CtMethod<?> method : event.getMethods()) {
            if (method.hasModifier(ModifierKind.STATIC)
                    && EventImplGenTask.getAnnotation(method, "org.spongepowered.api.util.annotation.eventgen.FactoryMethod") != null) {
                methods.add(method);
            }
        }
        return methods;
    }
}
