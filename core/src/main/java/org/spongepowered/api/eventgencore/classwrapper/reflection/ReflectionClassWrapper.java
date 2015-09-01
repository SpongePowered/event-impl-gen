/*
 * This file is part of SpongeAPI, licensed under the MIT License (MIT).
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

import java.lang.reflect.Method;
import java.util.List;

public class ReflectionClassWrapper implements ClassWrapper<Class<?>, Method> {

    private Class<?> clazz;

    public ReflectionClassWrapper(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getName() {
        return this.clazz.getName();
    }

    @Override
    public List<MethodWrapper<Class<?>, Method>> getMethods() {
        Method[] methods = this.clazz.getMethods();
        List<MethodWrapper<Class<?>, Method>> wrappers = Lists.newArrayListWithCapacity(methods.length);

        for (Method method: methods) {
            wrappers.add(new ReflectionMethodWrapper(method));
        }
        return wrappers;
    }

    @Override
    public boolean isSubtypeOf(ClassWrapper<Class<?>, Method> other) {
        return other.getActualClass().isAssignableFrom(this.clazz);
    }

    @Override
    public List<ClassWrapper<Class<?>, Method>> getInterfaces() {
        Class[] interfaces = this.clazz.getInterfaces();
        List<ClassWrapper<Class<?>, Method>> wrappers = Lists.newArrayListWithCapacity(interfaces.length);

        for (Class<?> klass: interfaces) {
            wrappers.add(new ReflectionClassWrapper(klass));
        }

        return wrappers;
    }

    @Override
    public ClassWrapper<Class<?>, Method> getSuperclass() {
        if (this.clazz.getSuperclass() != null) {
            return new ReflectionClassWrapper(this.clazz.getSuperclass());
        }
        return null;
    }

    @Override
    public Class getActualClass() {
        return this.clazz;
    }

    @Override
    public boolean isPrimitive(Class<?> other) {
        return this.clazz.equals(other);
    }

    @Override
    public ClassWrapper<Class<?>, Method> getBaseClass(Class<?> annotation) {
        return ReflectionUtils.getBaseClass(this.clazz, annotation);
    }
}
