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

import org.checkerframework.checker.nullness.qual.Nullable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * Finds properties by enumerating accessors and then later finding the
 * closest matching mutator.
 */
public class AccessorFirstStrategy implements PropertySearchStrategy {

    private static final Pattern ACCESSOR = Pattern.compile("^get([A-Z].*)");
    private static final Pattern ACCESSOR_BOOL = Pattern.compile("^is([A-Z].*)");
    private static final Pattern ACCESSOR_HAS = Pattern.compile("^has([A-Z].*)");
    private static final Pattern ACCESSOR_KEEPS = Pattern.compile("^(keeps[A-Z].*)");
    private static final Pattern MUTATOR = Pattern.compile("^set([A-Z].*)");

    /**
     * Detect whether the given method is an accessor and if so, return the
     * property name.
     *
     * @param method The method
     * @return The property name, if the method is an accessor
     */
    private String getAccessorName(final CtMethod<?> method) {
        Matcher m;

        if (this.isPublic(method) && method.getParameters().size() == 0) {
            final String methodName = method.getSimpleName();
            final CtTypeReference<?> returnType = method.getType();

            m = AccessorFirstStrategy.ACCESSOR.matcher(methodName);
            if (m.matches() && !returnType.getQualifiedName().equals("void")) {
                return AccessorFirstStrategy.getPropertyName(m.group(1));
            }

            m = AccessorFirstStrategy.ACCESSOR_BOOL.matcher(methodName);
            if (m.matches() && returnType.getQualifiedName().equals("boolean")) {
                return AccessorFirstStrategy.getPropertyName(m.group(1));
            }

            m = AccessorFirstStrategy.ACCESSOR_KEEPS.matcher(methodName);
            if (m.matches() && returnType.getQualifiedName().equals("boolean")) {
                return AccessorFirstStrategy.getPropertyName(m.group(1));
            }

            m = AccessorFirstStrategy.ACCESSOR_HAS.matcher(methodName);
            if (m.matches() && returnType.getQualifiedName().equals("boolean")) {
                return AccessorFirstStrategy.getPropertyName(methodName); // This is intentional, we want to keep the 'has'
            }
        }

        return null;
    }

    /**
     * Detect whether the given method is an mutator and if so, return the
     * property name.
     *
     * @param method The method
     * @return The property name, if the method is an mutator
     */
    private @Nullable String getMutatorName(final CtMethod<?> method) {
        final Matcher m;

        if (this.isPublic(method) && method.getParameters().size() == 1 && method.getType().getQualifiedName().equals("void")) {
            m = AccessorFirstStrategy.MUTATOR.matcher(method.getSimpleName());
            if (m.matches()) {
                return AccessorFirstStrategy.getPropertyName(m.group(1));
            }
        }

        return null;
    }

    private boolean isPublic(final CtMethod<?> method) {
        final Set<ModifierKind> modifiers = method.getModifiers();
        return modifiers.contains(ModifierKind.PUBLIC) || !(modifiers.contains(ModifierKind.PROTECTED) || modifiers.contains(ModifierKind.PRIVATE));
    }

    /**
     * Clean up the property name.
     *
     * @param name The name
     * @return The cleaned up name
     */
    public static String getPropertyName(final String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Find the corresponding mutator for an accessor method from a collection
     * of candidates.
     *
     * @param accessor The accessor
     * @param candidates The collection of candidates
     * @return A mutator, if found
     */
    protected @Nullable CtMethod<?> findMutator(final CtMethod<?> accessor, final @Nullable Collection<CtMethod<?>> candidates) {
        if (candidates == null) {
            return null;
        }

        final CtTypeReference<?> expectedType = accessor.getType();

        for (final CtMethod<?> method : candidates) {
            // TODO: Handle supertypes
            if (method.getParameters().get(0).getType().getQualifiedName().equals(expectedType.getQualifiedName()) || expectedType.getQualifiedName().equals(Optional.class.getName())) {
                return method;
            }
        }

        return null;
    }

    @Override
    public List<Property> findProperties(final CtTypeReference<?> type) {
        Objects.requireNonNull(type, "type");

        final Map<String, Set<CtMethod<?>>> accessors = new HashMap<>();
        final Map<String, Set<CtMethod<?>>> mutators = new HashMap<>();
        final Map<String, CtMethod<?>> accessorHierarchyBottoms = new HashMap<>();
        final Map<String, CtMethod<?>> mostSpecific = new HashMap<>();
        final Set<String> signatures = new HashSet<>();

        final Deque<CtType<?>> queue = new ArrayDeque<>();
        queue.push(type.getDeclaration());

        while (!queue.isEmpty()) {
            final CtType<?> ourType = queue.pop();
            for (final CtMethod<?> method : ourType.getMethods()) {
                String name;

                String signature = method.getSimpleName() + ";";
                for (final CtParameter<?> parameterType : method.getParameters()) {
                    signature += parameterType.getType().getQualifiedName() + ";";
                }
                signature += method.getType().getSimpleName();

                final CtMethod<?> leastSpecificMethod;
                if ((name = this.getAccessorName(method)) != null && !signatures.contains(signature)
                        && ((leastSpecificMethod = accessorHierarchyBottoms.get(name)) == null
                                    || !leastSpecificMethod.getType().getQualifiedName().equals(method.getType().getQualifiedName()))) {
                    accessors.computeIfAbsent(name, $ -> new HashSet<>()).add(method);
                    signatures.add(signature);

                    if (!mostSpecific.containsKey(name) || method.getType().isSubtypeOf(mostSpecific.get(name).getType())) {
                        mostSpecific.put(name, method);
                    }

                    if (accessorHierarchyBottoms.get(name) == null
                            || accessorHierarchyBottoms.get(name).getType().isSubtypeOf(method.getType())) {
                        accessorHierarchyBottoms.put(name, method);
                    }
                } else if ((name = this.getMutatorName(method)) != null) {
                    mutators.computeIfAbsent(name, $ -> new HashSet<>()).add(method);
                }
            }
            if (ourType.getSuperclass() != null) {
                queue.push(ourType.getSuperclass().getDeclaration());
            }
            for (final CtTypeReference<?> iface : ourType.getSuperInterfaces()) {
                queue.push(iface.getDeclaration());
            }
        }

        final List<Property> result = new ArrayList<>();

        for (final Map.Entry<String, Set<CtMethod<?>>> entry : accessors.entrySet()) {
            for (final CtMethod<?> accessor : entry.getValue()) {
                final @Nullable CtMethod<?> mutator = this.findMutator(accessor, mutators.get(entry.getKey()));
                result.add(new Property(entry.getKey(), accessor.getType(), accessorHierarchyBottoms.get(entry.getKey()),
                    mostSpecific.get(entry.getKey()), accessor, mutator
                ));
            }
        }

        result.sort(Comparator.comparing(Property::getName));
        return Collections.unmodifiableList(result);
    }

}
