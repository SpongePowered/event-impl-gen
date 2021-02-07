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

import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * When using event-impl-gen for plugins, the source for the event interfaces
 * included in SpongeAPI isn't available. Spoon relies on reflection to provide
 * a "shadow" class structure in these cases.
 *
 * <p>Currently, Spoon does not support getting annotation values from these
 * elements. This class implements a simple way to load the annotation values
 * using reflection. In the future, it would be preferable to analyse the
 * binary classes using ASM instead of classloading them in the Gradle process.</p>
 */
final class ShadowSpoon {

    private ShadowSpoon() {
    }

    @SuppressWarnings("unchecked")
    static <T> T getAnnotationValue(final CtAnnotation<?> annotationElement, final String key) {
        try {
            final Annotation annotation = ShadowSpoon.loadAnnotation(annotationElement);
            return (T) annotation.getClass().getMethod(key).invoke(annotation);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static CtTypeReference<?> getAnnotationTypeReference(final CtAnnotation<?> annotationElement, final String key) {
        try {
            final Annotation annotation = ShadowSpoon.loadAnnotation(annotationElement);
            final Class<?> reference = (Class<?>) annotation.getClass().getMethod(key).invoke(annotation);
            return annotationElement.getFactory().createCtTypeReference(reference);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Annotation loadAnnotation(final CtAnnotation<?> annotationElement) throws Exception {
        Annotation annotation = (Annotation) annotationElement.getMetadata("annotation");
        if (annotation == null) {
            final CtTypeReference<? extends Annotation> type = annotationElement.getAnnotationType();

            // Due to a bug in Spoon, it does not always set the correct
            // factory for the generated elements. This will cause the wrong
            // classpath to be used.
            type.setFactory(annotationElement.getParent().getFactory());

            annotation = ShadowSpoon.findAnnotation(annotationElement.getParent(), type.getActualClass());
            annotationElement.putMetadata("annotation", annotation);
        }

        return annotation;
    }

    private static Annotation findAnnotation(final CtElement parent, final Class<? extends Annotation> annotationClass) throws Exception {
        if (parent instanceof CtMethod) {
            final Class<?> clazz = ((CtType<?>) parent.getParent()).getActualClass();
            final Method method = clazz.getMethod(((CtMethod<?>) parent).getSimpleName(), ((CtMethod<?>) parent).getParameters().stream()
                    .map(p -> p.getType().getActualClass())
                    .toArray(Class<?>[]::new));
            return method.getAnnotation(annotationClass);
        } else if (parent instanceof CtType) {
            return ((CtType<?>) parent).getActualClass().getAnnotation(annotationClass);
        }

        throw new UnsupportedOperationException();
    }

}
