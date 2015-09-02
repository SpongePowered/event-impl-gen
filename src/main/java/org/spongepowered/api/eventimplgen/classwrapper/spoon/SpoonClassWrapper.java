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
package org.spongepowered.api.eventimplgen.classwrapper.spoon;

import com.google.common.collect.Lists;
import org.spongepowered.api.eventgencore.classwrapper.ClassWrapper;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class SpoonClassWrapper implements ClassWrapper<CtTypeReference<?>, CtMethod<?>> {

    protected CtTypeReference<?> clazz;

    public SpoonClassWrapper(CtTypeReference<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getName() {
        return this.clazz.getQualifiedName();
    }

    @Override
    public List<MethodWrapper<CtTypeReference<?>, CtMethod<?>>> getMethods() {
        if (this.clazz.getDeclaration() != null) {
            List<MethodWrapper<CtTypeReference<?>, CtMethod<?>>> methods = Lists.newArrayList();

            for (CtMethod<?> method: this.clazz.getDeclaration().getMethods()) {
                methods.add(new SpoonMethodWrapper(method));
            }

            return methods;
        }
        return Lists.newArrayList();
    }

    @Override
    public boolean isSubtypeOf(ClassWrapper<CtTypeReference<?>, CtMethod<?>> other) {
        return this.clazz.isSubtypeOf(other.getActualClass());
    }

    @Override
    public List<ClassWrapper<CtTypeReference<?>, CtMethod<?>>> getInterfaces() {
        List<ClassWrapper<CtTypeReference<?>, CtMethod<?>>> interfaces = Lists.newArrayList();

        for (CtTypeReference<?> klass: this.clazz.getSuperInterfaces()) {
            interfaces.add(new SpoonClassWrapper(klass));
        }

        return interfaces;
    }

    @Override
    public ClassWrapper<CtTypeReference<?>, CtMethod<?>> getSuperclass() {
        if (this.clazz.getSuperclass() != null) {
            return new SpoonClassWrapper(this.clazz.getSuperclass());
        }
        return null;
    }

    @Override
    public CtTypeReference<?> getActualClass() {
        return this.clazz;
    }

    @Override
    public boolean isPrimitive(Class<?> other) {
        if (this.clazz.isPrimitive()) {
            return this.clazz.getActualClass().equals(other);
        }
        return false;
    }

    @Override
    public ClassWrapper<CtTypeReference<?>, CtMethod<?>> getBaseClass(Class<?> annotationClass) {
        CtAnnotation<?> implementedBy = null;
        final Queue<CtTypeReference<?>> queue = new ArrayDeque<CtTypeReference<?>>();

        queue.add(this.clazz);
        CtTypeReference<?> scannedType;

        while ((scannedType = queue.poll()) != null) {
            for (CtAnnotation<? extends Annotation> annotation : scannedType.getDeclaration().getAnnotations()) {
                if (annotationClass.getName().equals(annotation.getType().getQualifiedName())) {
                    implementedBy = annotation;
                    break;
                }
            }
            for (CtTypeReference<?> implInterfaces : scannedType.getDeclaration().getSuperInterfaces()) {
                queue.offer(implInterfaces);
            }
        }

        if (implementedBy != null) {
            return new SpoonClassWrapper(((CtFieldReference<?>) implementedBy.getElementValues().get("value")).getDeclaringType());
        }
        return null;

    }
}
