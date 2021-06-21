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

import org.spongepowered.api.util.annotation.eventgen.FactoryMethod;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySearchStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

public final class EventInterfaceProcessor {

    private final PropertySearchStrategy strategy;
    private final Set<String> inclusiveAnnotations;
    private final Set<String> exclusiveAnnotations;

    @Inject
    public EventInterfaceProcessor(final PropertySearchStrategy strategy, final Elements elements, final Set<String> inclusiveAnnotations, final Set<String> exclusiveAnnotations) {
        this.strategy = strategy;
        this.inclusiveAnnotations = inclusiveAnnotations;
        this.exclusiveAnnotations = exclusiveAnnotations;
    }

    public void process(final TypeElement event) {
    }

    public boolean shouldGenerate(final TypeElement candidate) {
        if (AnnotationUtils.containsAnnotation(candidate, this.inclusiveAnnotations)) {
            return true;
        }
        if (AnnotationUtils.containsAnnotation(candidate, this.exclusiveAnnotations)) {
            return false;
        }
        return ElementFilter.typesIn(candidate.getEnclosedElements()).isEmpty();
    }

    private List<ExecutableElement> findForwardedMethods(final TypeElement event) {
        final List<ExecutableElement> methods = new ArrayList<>();
        for (final ExecutableElement method : ElementFilter.methodsIn(event.getEnclosedElements())) {
            if (method.getModifiers().contains(Modifier.STATIC)
                && method.getAnnotation(FactoryMethod.class) != null) {
                methods.add(method);
            }
        }
        return methods;
    }
}
