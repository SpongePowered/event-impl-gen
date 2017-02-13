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

import org.spongepowered.api.eventgencore.annotation.ImplementedBy;
import org.spongepowered.api.eventgencore.classwrapper.ClassWrapper;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

public class SpoonClassWrapper implements ClassWrapper<CtTypeReference<?>, CtMethod<?>> {

    private final CtTypeReference<?> type;

    public SpoonClassWrapper(CtTypeReference<?> type) {
        this.type = type;
    }

    @Override
    public String getName() {
        return this.type.getQualifiedName();
    }

    @Override
    public List<MethodWrapper<CtTypeReference<?>, CtMethod<?>>> getMethods() {
        if (this.type.getDeclaration() != null) {
            return this.type.getDeclaration().getMethods().stream().map(SpoonMethodWrapper::new).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isSubtypeOf(ClassWrapper<CtTypeReference<?>, CtMethod<?>> other) {
        return this.type.isSubtypeOf(other.getActualClass());
    }

    @Override
    public List<ClassWrapper<CtTypeReference<?>, CtMethod<?>>> getInterfaces() {
        return this.type.getSuperInterfaces().stream().map(SpoonClassWrapper::new).collect(Collectors.toList());
    }

    @Override
    public ClassWrapper<CtTypeReference<?>, CtMethod<?>> getSuperclass() {
        if (this.type.getSuperclass() != null) {
            return new SpoonClassWrapper(this.type.getSuperclass());
        }
        return null;
    }

    @Override
    public CtTypeReference<?> getActualClass() {
        return this.type;
    }

    @Override
    public boolean isPrimitive(Class<?> other) {
        return this.type.isPrimitive() && this.type.getActualClass().equals(other);
    }

    @Override
    public boolean isPrimitive() {
        return this.type.isPrimitive();
    }
}
