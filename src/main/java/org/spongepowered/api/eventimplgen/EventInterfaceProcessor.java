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

import org.spongepowered.api.eventgencore.AccessorFirstStrategy;
import org.spongepowered.api.eventgencore.Property;
import org.spongepowered.api.eventgencore.PropertySearchStrategy;
import org.spongepowered.api.eventimplgen.classwrapper.spoon.SpoonClassWrapper;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class EventInterfaceProcessor extends AbstractProcessor<CtInterface<?>> {

    private static final PropertySearchStrategy<CtTypeReference<?>, CtMethod<?>> SEARCH_STRATEGY = new AccessorFirstStrategy<>();
    private final Map<CtType<?>, Collection<? extends Property<CtTypeReference<?>, CtMethod<?>>>> foundProperties = new HashMap<>();
    private final EventImplGenExtension extension;

    public EventInterfaceProcessor(EventImplGenExtension extension) {
        this.extension = extension;
    }

    public Map<CtType<?>, Collection<? extends Property<CtTypeReference<?>, CtMethod<?>>>> getFoundProperties() {
        return foundProperties;
    }

    @Override
    public boolean isToBeProcessed(CtInterface<?> candidate) {
        return extension.isIncluded(candidate.getPosition().getCompilationUnit().getFile());
    }

    @Override
    public void process(CtInterface<?> event) {
        foundProperties.put(event, SEARCH_STRATEGY.findProperties(new SpoonClassWrapper(event.getReference())));
    }
}
