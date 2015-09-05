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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import org.spongepowered.api.eventgencore.classwrapper.ClassWrapper;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

public class SpoonMethodWrapper implements MethodWrapper<CtTypeReference<?>, CtMethod<?>> {

    private CtMethod<?> method;

    public SpoonMethodWrapper(CtMethod<?> method) {
        this.method = checkNotNull(method);
    }

    @Override
    public String getName() {
        return this.method.getSimpleName();
    }

    @Override
    public boolean isPublic() {
        Set<ModifierKind> modifiers = this.method.getModifiers();

        return modifiers.contains(ModifierKind.PUBLIC) || !(modifiers.contains(ModifierKind.PROTECTED) || modifiers.contains(ModifierKind.PRIVATE));
    }

    @Override
    public ClassWrapper<CtTypeReference<?>, CtMethod<?>> getReturnType() {
        return new SpoonClassWrapper(this.method.getType());
    }

    @Override
    public List<ClassWrapper<CtTypeReference<?>, CtMethod<?>>> getParameterTypes() {
        List<ClassWrapper<CtTypeReference<?>, CtMethod<?>>> parameters = Lists.newArrayList();

        for (CtParameter<?> klass: this.method.getParameters()) {
            parameters.add(new SpoonClassWrapper(klass.getType()));
        }

        return parameters;
    }

    @Override
    public CtMethod<?> getActualMethod() {
        return this.method;
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotation) {
        return this.method.getAnnotation(annotation);
    }

    @Override
    public ClassWrapper<CtTypeReference<?>, CtMethod<?>> getEnclosingClass() {
        return new SpoonClassWrapper(this.method.getDeclaringType().getReference());
    }
}
