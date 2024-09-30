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

import org.jetbrains.annotations.Nullable;
import org.spongepowered.eventgen.annotations.ImplementedBy;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public final class AnnotationUtils {

    private AnnotationUtils() {
    }

    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public static <T> T getValue(final AnnotationMirror anno, final String key) {
        if (anno == null) {
            return null;
        }

        for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> element : anno.getElementValues().entrySet()) {
            if (element.getKey().getSimpleName().contentEquals(key)) {
                return (T) element.getValue().getValue();
            }
        }
        return null;
    }

    public static @Nullable AnnotationMirror getAnnotation(final AnnotatedConstruct type, final Class<? extends Annotation> clazz) {
        return AnnotationUtils.getAnnotation(type, clazz.getName());
    }

    public static @Nullable DeclaredType getImplementedBy(final TypeElement iface) {
        final Queue<TypeElement> queue = new ArrayDeque<>();
        AnnotationMirror implementedBy = null;
        int max = Integer.MIN_VALUE;

        queue.add(iface);
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
        return null;
    }

    public static @Nullable AnnotationMirror getAnnotation(final AnnotatedConstruct type, final String name) {
        for (final AnnotationMirror annotation : type.getAnnotationMirrors()) {
            if (((TypeElement) annotation.getAnnotationType().asElement()).getQualifiedName().toString().equals(name)) {
                return annotation;
            }
        }
        return null;
    }

    public static boolean containsAnnotation(final AnnotatedConstruct type, final Set<String> looking) {
        for (final AnnotationMirror annotation : type.getAnnotationMirrors()) {
            if (looking.contains(((TypeElement) annotation.getAnnotationType().asElement()).getQualifiedName().toString())) {
                return true;
            }
        }
        return false;
    }
}
