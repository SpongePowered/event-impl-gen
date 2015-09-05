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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.spongepowered.api.eventgencore.AccessorFirstStrategy;
import org.spongepowered.api.eventgencore.Property;
import org.spongepowered.api.eventgencore.annotation.AbsoluteSortPosition;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.MethodFactory;
import spoon.reflect.reference.CtTypeReference;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PropertySorter {

    private String prefix;

    public PropertySorter(String prefix) {
        this.prefix = prefix;
    }

    public List<? extends Property<CtTypeReference<?>,CtMethod<?>>> sortProperties(List<? extends Property<CtTypeReference<?>, CtMethod<?>>> properties) {
        List<Property<CtTypeReference<?>,CtMethod<?>>> finalProperties = Lists.newArrayList();
        Map<String, Property<CtTypeReference<?>,CtMethod<?>>> propertyMap = Maps.newHashMap();
        List<PrefixPair> pairs = Lists.newArrayList();

        for (Property<CtTypeReference<?>,CtMethod<?>> property: properties) {
            if (property.isMostSpecificType()) {
                propertyMap.put(property.getName(), property);

                AbsoluteSortPosition sortPosition = property.getAccessorWrapper().getAnnotation(AbsoluteSortPosition.class);
                if (sortPosition != null) {
                    finalProperties.add(Math.min(sortPosition.value(), finalProperties.size()), property);
                    propertyMap.remove(property.getName());
                }
            }
        }

        for (Map.Entry<String, Property<CtTypeReference<?>,CtMethod<?>>> entry: new HashSet<Map.Entry<String, Property<CtTypeReference<?>,CtMethod<?>>>>(propertyMap.entrySet())) {
            String name = entry.getValue().getName();
            String unprefixedName = getUnprefixedName(name);

            if (name.startsWith(prefix)) {
                if (propertyMap.containsKey(unprefixedName)) {
                    pairs.add(new PrefixPair(entry.getValue(), propertyMap.get(unprefixedName)));
                    propertyMap.remove(name);
                    propertyMap.remove(unprefixedName);
                }
            }
        }

        Collections.sort(pairs);

        for (PrefixPair pair: pairs) {
            finalProperties.add(pair.prefixed);
            finalProperties.add(pair.unprefixed);
        }

        List<Property<CtTypeReference<?>, CtMethod<?>>> normalProperties = Lists.newArrayList(propertyMap.values());
        Collections.sort(normalProperties);

        finalProperties.addAll(normalProperties);

        return finalProperties;
    }

    private static class PrefixPair implements Comparable<PrefixPair> {

        public Property<CtTypeReference<?>,CtMethod<?>> prefixed;
        public Property<CtTypeReference<?>,CtMethod<?>> unprefixed;

        public PrefixPair() {}

        public PrefixPair(Property<CtTypeReference<?>, CtMethod<?>> prefixed, Property<CtTypeReference<?>, CtMethod<?>> unprefixed) {
            this.prefixed = prefixed;
            this.unprefixed = unprefixed;
        }

        @Override
        public int compareTo(PrefixPair other) {
            return this.unprefixed.getName().compareTo(other.unprefixed.getName());
        }
    }

    private boolean isPrefixPair(Property<?, ?> property, Property<?, ?> otherProperty) {
        String name = property.getName();
        if (name.startsWith(prefix)) {
            String newName = this.getUnprefixedName(name);
            return newName.equals(otherProperty.getName());
        }
        return false;
    }

    private String getUnprefixedName(String name) {
        if (name.startsWith(prefix)) {
            return AccessorFirstStrategy.getPropertyName(name.replaceFirst(prefix, ""));
        }
        return name;
    }
}
