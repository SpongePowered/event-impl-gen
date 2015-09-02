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

import com.google.common.collect.Maps;
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
import java.util.Map;

public class EventInterfaceProcessor extends AbstractProcessor<CtInterface<?>> {

    private final Map<CtInterface<?>, Map<String, CtTypeReference<?>>> eventFields = Maps.newHashMap();
    private final Map<CtType<?>, Collection<? extends Property<CtTypeReference<?>, CtMethod<?>>>> foundProperties = Maps.newHashMap();
    private EventImplGenExtension extension;

    @Override
    public void init() {
        try {
            final ObjectProcessorProperties properties =
                (ObjectProcessorProperties) getEnvironment().getProcessorProperties(getClass().getCanonicalName());
            properties.put("eventFields", eventFields);
            properties.put("properties", foundProperties);
            extension = properties.get(EventImplGenExtension.class, "extension");
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new RuntimeException(exception);
        }
    }

    @Override
    public boolean isToBeProcessed(CtInterface<?> candidate) {
        return extension.isIncluded(candidate.getPosition().getCompilationUnit().getFile());
    }

    @Override
    public void process(CtInterface<?> event) {
        final PropertySearchStrategy<CtTypeReference<?>, CtMethod<?>> searchStrategy = new AccessorFirstStrategy<CtTypeReference<?>, CtMethod<?>>();
        final Collection<? extends Property<CtTypeReference<?>, CtMethod<?>>> eventProps = searchStrategy.findProperties(new SpoonClassWrapper
            (event.getReference()));
        foundProperties.put(event, eventProps);
    }
}
