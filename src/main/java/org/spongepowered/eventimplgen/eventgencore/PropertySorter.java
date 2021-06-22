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
package org.spongepowered.eventimplgen.eventgencore;

import org.spongepowered.api.util.annotation.eventgen.AbsoluteSortPosition;
import org.spongepowered.eventimplgen.processor.EventGenOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.lang.model.util.Types;

public class PropertySorter {

    private final String prefix;
    private final Map<String, String> groupingPrefixes;
    private final Types types;

    @Inject
    PropertySorter(final Types types, final EventGenOptions options) {
        this(types, options.sortPriorityPrefix(), options.groupingPrefixes());
    }

    public PropertySorter(final Types types, final String prefix, final Map<String, String> groupingPrefixes) {
        this.types = types;
        this.prefix = prefix;
        this.groupingPrefixes = groupingPrefixes;
    }

    @SuppressWarnings("rawtypes")
    public List<Property> sortProperties(final Collection<Property> properties) {

        final List<Property> finalProperties = new ArrayList<>();
        final Map<String, Property> propertyMap = new HashMap<>();
        final List<PrefixPair> pairs = new ArrayList<>();
        final List<Property> primitiveProperties = new ArrayList<>();

        properties.stream().filter(prop -> prop.isMostSpecificType(this.types)).forEach(property -> {
            propertyMap.put(property.getName(), property);
            final AbsoluteSortPosition sortPosition = property.getAccessor().getAnnotation(AbsoluteSortPosition.class);
            if (sortPosition != null) {
                finalProperties.add(Math.min(sortPosition.value(), finalProperties.size()), property);
                propertyMap.remove(property.getName());
            }
        });

        for (final Map.Entry<String, Property> entry : new HashSet<>(propertyMap.entrySet())) {
            final String name = entry.getValue().getName();
            final String unprefixedName = this.getUnprefixedName(name);
            if (name.startsWith(this.prefix)) {
                if (propertyMap.containsKey(unprefixedName)) {
                    pairs.add(new PrefixPair(entry.getValue(), propertyMap.get(unprefixedName)));
                    propertyMap.remove(name);
                    propertyMap.remove(unprefixedName);
                }
            }
        }

        for (final Map.Entry<String, Property> entry : new HashSet<>(propertyMap.entrySet())) {
            final String name = entry.getKey();
            final Property property = entry.getValue();
            if (property.getWrapperType().getKind().isPrimitive()) {
                primitiveProperties.add(property);
                propertyMap.remove(name);
            } else {
                for (final Map.Entry<String, String> prefixEntry : this.groupingPrefixes.entrySet()) {
                    if (name.startsWith(prefixEntry.getKey())) {
                        final String modifiedName = name.replaceFirst(prefixEntry.getKey(), prefixEntry.getValue());
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

        for (final PrefixPair pair : pairs) {
            finalProperties.add(pair.prefixed);
            finalProperties.add(pair.unprefixed);
        }

        final List<Property> normalProperties = new ArrayList<>(propertyMap.values());

        Collections.sort(normalProperties);
        Collections.sort(primitiveProperties);

        finalProperties.addAll(normalProperties);
        finalProperties.addAll(primitiveProperties);

        return finalProperties;
    }

    private static class PrefixPair implements Comparable<PrefixPair> {

        private final Property prefixed;
        private final Property unprefixed;

        private PrefixPair(final Property prefixed, final Property unprefixed) {
            this.prefixed = prefixed;
            this.unprefixed = unprefixed;
        }

        @Override
        public int compareTo(final PrefixPair other) {
            return this.unprefixed.getName().compareTo(other.unprefixed.getName());
        }
    }

    private String getUnprefixedName(final String name) {
        if (name.startsWith(this.prefix)) {
            return AccessorFirstStrategy.getPropertyName(name.replaceFirst(this.prefix, ""));
        }
        return name;
    }
}
