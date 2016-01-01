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

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.spongepowered.api.eventgencore.AccessorFirstStrategy;
import org.spongepowered.api.eventgencore.Property;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PropertySorter {

    private String prefix;
    private Map<String, String> groupingPrefixes;

    public PropertySorter(String prefix, Map<String, String> groupingPrefixes) {
        this.prefix = prefix;
        this.groupingPrefixes = groupingPrefixes;
    }

    public List<? extends Property<Type, MethodDeclaration>> sortProperties(List<? extends Property<Type, MethodDeclaration>> properties) {
        List<Property<Type, MethodDeclaration>> finalProperties = Lists.newArrayList();
        Map<String, Property<Type, MethodDeclaration>> propertyMap = Maps.newHashMap();
        List<PrefixPair> pairs = Lists.newArrayList();
        List<Property<Type, MethodDeclaration>> primitiveProperties = Lists.newArrayList();

        for (Property<Type, MethodDeclaration> property : properties) {
            if (property.isMostSpecificType()) {
                propertyMap.put(property.getName(), property);

                int sortPosition = property.getAccessorWrapper().getAbsoluteSortPosition();
                if (sortPosition >= 0) {
                    finalProperties.add(Math.min(sortPosition, finalProperties.size()), property);
                    propertyMap.remove(property.getName());
                }
            }
        }

        for (Map.Entry<String, Property<Type, MethodDeclaration>> entry : new HashSet<>(propertyMap.entrySet())) {
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

        for (Map.Entry<String, Property<Type, MethodDeclaration>> entry : new HashSet<>(propertyMap.entrySet())) {
            String name = entry.getKey();
            Property<Type, MethodDeclaration> property = entry.getValue();

            if (property.getType() instanceof PrimitiveType) {
                primitiveProperties.add(property);
                propertyMap.remove(name);
            } else {
                for (Map.Entry<String, String> prefixEntry : groupingPrefixes.entrySet()) {
                    if (name.startsWith(prefixEntry.getKey())) {
                        String modifiedName = name.replaceFirst(prefixEntry.getKey(), prefixEntry.getValue());
                        if (propertyMap.containsKey(modifiedName)) {
                            pairs.add(new PrefixPair(entry.getValue(), propertyMap.get(modifiedName)));
                            propertyMap.remove(name);
                            propertyMap.remove(modifiedName);
                            break;
                        }
                    }
                }
            }
        }

        Collections.sort(pairs);

        for (PrefixPair pair : pairs) {
            finalProperties.add(pair.prefixed);
            finalProperties.add(pair.unprefixed);
        }

        List<Property<Type, MethodDeclaration>> normalProperties = Lists.newArrayList(propertyMap.values());

        Collections.sort(normalProperties);
        Collections.sort(primitiveProperties);

        finalProperties.addAll(normalProperties);
        finalProperties.addAll(primitiveProperties);

        return finalProperties;
    }

    private static class PrefixPair implements Comparable<PrefixPair> {

        public Property<Type, MethodDeclaration> prefixed;
        public Property<Type, MethodDeclaration> unprefixed;

        public PrefixPair() {
        }

        public PrefixPair(Property<Type, MethodDeclaration> prefixed, Property<Type, MethodDeclaration> unprefixed) {
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
