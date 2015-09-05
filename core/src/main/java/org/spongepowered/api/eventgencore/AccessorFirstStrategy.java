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
package org.spongepowered.api.eventgencore;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.spongepowered.api.eventgencore.classwrapper.ClassWrapper;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 *
 * Finds properties by enumerating accessors and then later finding the
 * closest matching mutator.
 */
public class AccessorFirstStrategy<T, M> implements PropertySearchStrategy<T, M> {

    protected static final Pattern ACCESSOR = Pattern.compile("^get([A-Z].*)");
    protected static final Pattern ACCESSOR_BOOL = Pattern.compile("^is([A-Z].*)");
    protected static final Pattern ACCESSOR_HAS = Pattern.compile("^has([A-Z].*)");
    protected static final Pattern ACCESSOR_KEEPS = Pattern.compile("^(keeps[A-Z].*)");
    protected static final Pattern MUTATOR = Pattern.compile("^set([A-Z].*)");

    /**
     * Detect whether the given method is an accessor and if so, return the
     * property name.
     *
     * @param method The method
     * @return The property name, if the method is an accessor
     */
    protected String getAccessorName(MethodWrapper<T, M> method) {
        Matcher m;

        if (method.isPublic() && method.getParameterTypes().size() == 0) {
            String methodName = method.getName();
            ClassWrapper<T, M> returnType = method.getReturnType();

            m = ACCESSOR.matcher(methodName);
            if (m.matches() && !returnType.isPrimitive(void.class)) {
                return this.getPropertyName(m.group(1));
            }

            m = ACCESSOR_BOOL.matcher(methodName);
            if (m.matches() && returnType.isPrimitive(boolean.class)) {
                return this.getPropertyName(m.group(1));
            }

            m = ACCESSOR_KEEPS.matcher(methodName);
            if (m.matches() && returnType.isPrimitive(boolean.class)) {
                return this.getPropertyName(m.group(1));
            }

            m = ACCESSOR_HAS.matcher(methodName);
            if (m.matches() && returnType.isPrimitive(boolean.class)) {
                return this.getPropertyName(methodName); // This is intentional, we want to keep the 'has'
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
    @Nullable
    private String getMutatorName(MethodWrapper<T, M> method) {
        Matcher m;

        if (method.isPublic() && method.getParameterTypes().size() == 1 && method.getReturnType().getActualClass().equals(void.class)) {
            m = MUTATOR.matcher(method.getName());
            if (m.matches()) {
                return getPropertyName(m.group(1));
            }
        }

        return null;
    }

    /**
     * Clean up the property name.
     *
     * @param name The name
     * @return The cleaned up name
     */
    public static String getPropertyName(String name) {
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
    @Nullable
    protected MethodWrapper<T, M> findMutator(MethodWrapper<T, M> accessor, Collection<MethodWrapper<T, M>> candidates) {
        T expectedType = accessor.getReturnType().getActualClass();

        for (MethodWrapper<T, M> method : candidates) {
            // TODO: Handle supertypes
            if (method.getParameterTypes().get(0).getActualClass().equals(expectedType) || expectedType.equals(Optional.class)) {
                return method;
            }
        }

        return null;
    }

    @Override
    public ImmutableSet<? extends Property<T, M>> findProperties(final ClassWrapper<T, M> type) {
        checkNotNull(type, "type");

        final Multimap<String, MethodWrapper<T, M>> accessors = HashMultimap.create();
        final Multimap<String, MethodWrapper<T, M>> mutators = HashMultimap.create();
        final Queue<ClassWrapper<T, M>> queue = new NonNullUniqueQueue<ClassWrapper<T, M>>();
        final Map<String, MethodWrapper<T, M>> accessorHierarchyBottoms = new HashMap<String, MethodWrapper<T, M>>();
        final Map<String, MethodWrapper<T, M>> mostSpecific = Maps.newHashMap();
        final Set<String> signatures = Sets.newHashSet();

        queue.add(type); // Start off with our target type

        ClassWrapper<T, M> scannedType;
        while ((scannedType = queue.poll()) != null) {
            for (MethodWrapper<T, M> method : scannedType.getMethods()) {
                String name;

                String signature = method.getName() + ";";
                for (ClassWrapper<T, M> parameterType: method.getParameterTypes()) {
                    signature += parameterType.getName() + ";";
                }
                signature += method.getReturnType().getName();

                MethodWrapper<T, M> leastSpecificMethod;
                if ((name = getAccessorName(method)) != null && !signatures.contains(signature)
                        && ((leastSpecificMethod = accessorHierarchyBottoms.get(name)) == null
                                    || !leastSpecificMethod.getReturnType().equals(method.getReturnType()))) {
                    accessors.put(name, method);
                    signatures.add(signature);

                    if (!mostSpecific.containsKey(name) || method.getReturnType().isSubtypeOf(mostSpecific.get(name).getReturnType())) {
                        mostSpecific.put(name, method);
                    }

                    if (accessorHierarchyBottoms.get(name) == null
                            || accessorHierarchyBottoms.get(name).getReturnType().isSubtypeOf(method.getReturnType())) {
                        accessorHierarchyBottoms.put(name, method);
                    }
                } else if ((name = getMutatorName(method)) != null) {
                    mutators.put(name, method);
                }
            }

            for (ClassWrapper<T, M> implInterfaces : scannedType.getInterfaces()) {
                queue.offer(implInterfaces);
            }
            queue.offer(scannedType.getSuperclass());
        }

        final ImmutableSet.Builder<Property<T, M>> result = ImmutableSet.builder();

        for (Map.Entry<String, MethodWrapper<T, M>> entry : accessors.entries()) {
            MethodWrapper<T, M> accessor = entry.getValue();

            @Nullable MethodWrapper<T, M> mutator = findMutator(entry.getValue(), mutators.get(entry.getKey()));
            result.add(new Property<T, M>(entry.getKey(), accessor.getReturnType(), accessorHierarchyBottoms.get(entry.getKey()), mostSpecific.get(entry.getKey()), accessor, mutator));
        }

        return result.build();
    }

}
