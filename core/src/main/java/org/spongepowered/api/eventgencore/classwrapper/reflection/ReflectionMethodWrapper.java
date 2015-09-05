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
package org.spongepowered.api.eventgencore.classwrapper.reflection;

import com.google.common.collect.Lists;
import org.spongepowered.api.eventgencore.classwrapper.ClassWrapper;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class ReflectionMethodWrapper implements MethodWrapper<Class<?>, Method> {

    private final Method method;

    public ReflectionMethodWrapper(Method method) {
        this.method = method;
    }

    @Override
    public String getName() {
        return this.method.getName();
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(this.method.getModifiers());
    }

    @Override
    public ClassWrapper<Class<?>, Method> getReturnType() {
        return new ReflectionClassWrapper(this.method.getReturnType());
    }

    @Override
    public List<ClassWrapper<Class<?>, Method>> getParameterTypes() {
        Class<?>[] parameters = this.method.getParameterTypes();
        List<ClassWrapper<Class<?>, Method>> wrappers = Lists.newArrayListWithCapacity(parameters.length);

        for (Class<?> parameter: parameters) {
            wrappers.add(new ReflectionClassWrapper(parameter));
        }
        return wrappers;
    }

    @Override
    public Method getActualMethod() {
        return this.method;
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotation) {
        return this.method.getAnnotation(annotation);
    }

    @Override
    public ClassWrapper<Class<?>, Method> getEnclosingClass() {
        return new ReflectionClassWrapper(this.method.getDeclaringClass());
    }
}
