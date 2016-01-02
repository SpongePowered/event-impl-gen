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
package org.spongepowered.api.eventimplgen;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import com.google.common.collect.Maps;
import org.spongepowered.api.eventgencore.AccessorFirstStrategy;
import org.spongepowered.api.eventgencore.Property;
import org.spongepowered.api.eventgencore.PropertySearchStrategy;
import org.spongepowered.api.eventimplgen.classwrapper.javaparser.JavaParserClassWrapper;

import java.util.Collection;
import java.util.Map;

public class EventInterfaceProcessor {

    private final Map<ClassOrInterfaceDeclaration, Collection<? extends Property<Type, MethodDeclaration>>> foundProperties = Maps.newHashMap();
    private final EventImplGenExtension extension;
    private final WorkingSource source;

    public EventInterfaceProcessor(EventImplGenExtension extension, WorkingSource source) {
        this.extension = extension;
        this.source = source;
    }

    public boolean shouldProcess(String qualifiedName) {
        return extension.isIncluded(qualifiedName);
    }

    public void process(ClassOrInterfaceDeclaration event) {
        final PropertySearchStrategy<Type, MethodDeclaration> searchStrategy = new AccessorFirstStrategy<>();
        final Collection<? extends Property<Type, MethodDeclaration>> eventProps =
            searchStrategy.findProperties(new JavaParserClassWrapper(source, event));
        foundProperties.put(event, eventProps);
    }
}
