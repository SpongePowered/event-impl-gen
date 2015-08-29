/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015-2015 SpongePowered <http://spongepowered.org/>
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import spoon.processing.ProcessorProperties;

import java.util.Map;
import java.util.NoSuchElementException;

public class ObjectProcessorProperties implements ProcessorProperties {

    private final Map<String, Object> properties = Maps.newHashMap();
    private final String processorName;

    public ObjectProcessorProperties(String processorName) {
        this.processorName = processorName;
    }

    public void put(String name, Object property) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(property, "property");
        properties.put(name, property);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Class<T> type, String name) {
        final Object property = properties.get(name);
        if (property == null) {
            throw new NoSuchElementException(name);
        }
        if (!type.isInstance(property)) {
            throw new ClassCastException(property.getClass().getCanonicalName() + " to " + type.getCanonicalName());
        }
        return (T) property;
    }

    @Override
    public String getProcessorName() {
        return this.processorName;
    }

}
