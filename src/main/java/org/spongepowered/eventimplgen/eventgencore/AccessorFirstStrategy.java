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

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.eventgen.annotations.ImplementedBy;
import org.spongepowered.eventimplgen.AnnotationUtils;
import org.spongepowered.eventimplgen.signature.Descriptors;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

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

    private final Descriptors descriptors;
    private final Types types;
    private final boolean allowFluentStyle;

    private final TypeElement optional;

    @AssistedFactory
    public interface Factory {
        AccessorFirstStrategy create(final boolean allowFluentStyle);
    }

    @AssistedInject
    public AccessorFirstStrategy(final Types types, final Elements elements, final Descriptors descriptors, @Assisted final boolean allowFluentStyle) {
        this.types = types;
        this.descriptors = descriptors;
        this.allowFluentStyle = allowFluentStyle;
        this.optional = elements.getTypeElement("java.util.Optional");
    }

    /**
     * Detect whether the given method is an accessor and if so, return the
     * property name.
     *
     * @param method The method
     * @return The property name, if the method is an accessor
     */
    private String getAccessorName(final ExecutableElement method) {
        Matcher m;

        if (this.isPublic(method) && method.getParameters().isEmpty()) {
            final CharSequence methodName = method.getSimpleName();
            final TypeMirror returnType = method.getReturnType();

            if (returnType.getKind() == TypeKind.VOID) {
                return null;
            }

            m = AccessorFirstStrategy.ACCESSOR.matcher(methodName);
            if (m.matches()) {
                return AccessorFirstStrategy.getPropertyName(m.group(1));
            }

            m = AccessorFirstStrategy.ACCESSOR_BOOL.matcher(methodName);
            if (m.matches() && returnType.getKind() == TypeKind.BOOLEAN) {
                return AccessorFirstStrategy.getPropertyName(m.group(1));
            }

            m = AccessorFirstStrategy.ACCESSOR_KEEPS.matcher(methodName);
            if (m.matches() && returnType.getKind() == TypeKind.BOOLEAN) {
                return AccessorFirstStrategy.getPropertyName(m.group(1));
            }

            m = AccessorFirstStrategy.ACCESSOR_HAS.matcher(methodName);
            if (m.matches() && returnType.getKind() == TypeKind.BOOLEAN) {
                return AccessorFirstStrategy.getPropertyName(methodName); // This is intentional, we want to keep the 'has'
            }

            if (this.allowFluentStyle) {
                return methodName.toString();
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
    private @Nullable String getMutatorName(final ExecutableElement method) {
        final Matcher m;

        if (this.isPublic(method) && method.getParameters().size() == 1 && method.getReturnType().getKind() == TypeKind.VOID) {
            m = AccessorFirstStrategy.MUTATOR.matcher(method.getSimpleName());
            if (m.matches()) {
                return AccessorFirstStrategy.getPropertyName(m.group(1));
            } else if (this.allowFluentStyle) {
                return method.getSimpleName().toString();
            }
        }

        return null;
    }

    private boolean isPublic(final ExecutableElement method) {
        final Set<Modifier> modifiers = method.getModifiers();
        return modifiers.contains(Modifier.PUBLIC) || !(modifiers.contains(Modifier.PROTECTED) || modifiers.contains(Modifier.PRIVATE));
    }

    /**
     * Clean up the property name.
     *
     * @param name The name
     * @return The cleaned up name
     */
    public static String getPropertyName(final CharSequence name) {
        return Character.toLowerCase(name.charAt(0)) + name.subSequence(1, name.length()).toString();
    }

    /**
     * Find the corresponding mutator for an accessor method from a collection
     * of candidates.
     *
     * @param accessor      The accessor
     * @param candidates    The collection of candidates
     * @param implementedBy The abstract super type implementation to consider
     * @return A mutator, if found
     */
    protected @Nullable ExecutableElement findMutator(final ExecutableElement accessor,
                                                      final @Nullable Collection<ExecutableElement> candidates,
                                                      final DeclaredType implementedBy) {
        if (candidates == null) {
            return null;
        }

        final TypeMirror expectedType = accessor.getReturnType();

        // Optional tests
        final boolean isOptionalReturn = expectedType.getKind() == TypeKind.DECLARED && this.types.isAssignable(this.types.erasure(expectedType), this.optional.asType());
        for (final ExecutableElement method : candidates) {
            if (implementedBy != null) {
                for (Element enclosedElement : implementedBy.asElement().getEnclosedElements()) {
                    if (enclosedElement instanceof ExecutableElement ee) {
                        // This means the implemented by has the actual method
                        if (this.isMatchingMutator(ee, isOptionalReturn, expectedType) != null) {
                            return null;
                        }
                    }
                }
            }
            final var candidate = this.isMatchingMutator(method, isOptionalReturn, expectedType);
            if (candidate != null) {
                return candidate;
            }

        }

        return null;
    }

    private @Nullable ExecutableElement isMatchingMutator(ExecutableElement method, boolean optionalReturn, TypeMirror expectedType) {
        if (method.getParameters().isEmpty()) {
            return null;
        }
        // TODO: Handle supertypes
        final TypeMirror firstParam = method.getParameters().get(0).asType();
        if (this.types.isSameType(firstParam, expectedType)) {
            return method;
        }

        if (optionalReturn && this.types.isSameType(firstParam, ((DeclaredType) expectedType).getTypeArguments().get(0))) {
            return method;
        }
        return null;
    }

    @Override
    public List<Property> findProperties(final TypeElement type) {
        Objects.requireNonNull(type, "type");

        final Map<String, Set<ExecutableElement>> accessors = new HashMap<>();
        final Map<String, Set<ExecutableElement>> mutators = new HashMap<>();
        final Map<String, ExecutableElement> accessorHierarchyBottoms = new HashMap<>();
        final Map<String, ExecutableElement> mostSpecific = new HashMap<>();
        final Set<String> signatures = new HashSet<>();

        final Deque<TypeElement> queue = new ArrayDeque<>();
        queue.push(type);

        while (!queue.isEmpty()) {
            final TypeElement ourType = queue.pop();
            for (final Element element : ourType.getEnclosedElements()) {
                if (element.getKind() != ElementKind.METHOD) {
                    continue;
                }
                final ExecutableElement method = (ExecutableElement) element;

                String name;

                final StringBuilder signature = new StringBuilder(method.getSimpleName() + ";");
                signature.append(this.descriptors.getDescriptor(method));

                final ExecutableElement leastSpecificMethod;
                if ((name = this.getAccessorName(method)) != null && !signatures.contains(signature.toString())
                        && ((leastSpecificMethod = accessorHierarchyBottoms.get(name)) == null
                                    || !this.types.isSameType(leastSpecificMethod.getReturnType(), method.getReturnType()))) {
                    accessors.computeIfAbsent(name, $ -> new HashSet<>()).add(method);
                    signatures.add(signature.toString());

                    if (!mostSpecific.containsKey(name) || this.types.isSubtype(method.getReturnType(), mostSpecific.get(name).getReturnType())) {
                        mostSpecific.put(name, method);
                    }

                    if (accessorHierarchyBottoms.get(name) == null
                            || this.types.isSubtype(accessorHierarchyBottoms.get(name).getReturnType(), method.getReturnType())) {
                        accessorHierarchyBottoms.put(name, method);
                    }
                } else if ((name = this.getMutatorName(method)) != null) {
                    mutators.computeIfAbsent(name, $ -> new HashSet<>()).add(method);
                }
            }
            if (ourType.getSuperclass().getKind() != TypeKind.NONE) {
                queue.push((TypeElement) this.types.asElement(ourType.getSuperclass()));
            }
            for (final TypeMirror iface : ourType.getInterfaces()) {
                queue.push((TypeElement) this.types.asElement(iface));
            }
        }

        final List<Property> result = new ArrayList<>();

        for (final Map.Entry<String, Set<ExecutableElement>> entry : accessors.entrySet()) {
            for (final ExecutableElement accessor : entry.getValue()) {
                final ExecutableType relativizedAccessor = (ExecutableType) this.types.asMemberOf(((DeclaredType) type.asType()), accessor);
                final @Nullable DeclaredType implementedBy = AnnotationUtils.getImplementedBy(type);

                final @Nullable ExecutableElement mutator = this.findMutator(accessor, mutators.get(entry.getKey()), implementedBy);
                result.add(new Property(
                    entry.getKey(),
                    relativizedAccessor.getReturnType(),
                    accessorHierarchyBottoms.get(entry.getKey()),
                    mostSpecific.get(entry.getKey()),
                    accessor,
                    mutator
                ));
            }
        }

        result.sort(Comparator.comparing(Property::getName));
        return Collections.unmodifiableList(result);
    }

}
