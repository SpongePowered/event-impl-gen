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

import java.util.Objects;
import java.util.Optional;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * A property is a getter with possibly a setter pair.
 *
 */
public final class Property implements Comparable<Property> {

    private final String name;
    private final TypeMirror type;
    private final ExecutableElement leastSpecificMethod;
    private final ExecutableElement mostSpecificMethod;
    private final ExecutableElement accessor;
    private final Optional<ExecutableElement> mutator;

    /**
     * Create a new property.
     *
     * @param name The name of the property
     * @param type The type of property
     * @param leastSpecificMethod  The least specific method
     * @param accessor The accessor
     * @param mutator The mutator
     */
    public Property(
        final String name,
        final TypeMirror type,
        final ExecutableElement leastSpecificMethod,
        final ExecutableElement mostSpecificMethod,
        final ExecutableElement accessor,
        final @Nullable ExecutableElement mutator
   ) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.leastSpecificMethod = Objects.requireNonNull(leastSpecificMethod, "leastSpecificMethod");
        this.mostSpecificMethod = Objects.requireNonNull(mostSpecificMethod, "mostSpecificMethod");
        this.accessor = Objects.requireNonNull(accessor, "accessor");
        this.mutator = Optional.ofNullable(mutator);
    }

    /**
     * Get the name of the property.
     *
     * @return The name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the type of the paramteer.
     *
     * @return The type
     */
    public TypeMirror getType() {
        return this.type;
    }

    public TypeMirror getWrapperType() {
        return this.type;
    }

    /**
     * Gets the least specific version of the accessor used.
     *
     * @return The least specific accessor
     */
    public ExecutableElement getLeastSpecificMethod() {
        return this.leastSpecificMethod;
    }

    /**
     * Gets the least specific version of the type used
     *
     * <p>This is used for the type of the generated field used to hold the value.</p>
     *
     * @return The type
     */
    public TypeMirror getLeastSpecificType() {
        return this.leastSpecificMethod.getReturnType();
    }

    /**
     * Get the most specific version of the accessor used.
     *
     * @return The least specific accessor
     */
    public ExecutableElement getMostSpecificMethod() {
        return this.mostSpecificMethod;
    }

    /**
     * Gets the most specific version of the type used
     *
     * @return The type
     */
    public TypeMirror getMostSpecificType() {
        return this.mostSpecificMethod.getReturnType();
    }

    /**
     * Get the method representing the accessor.
     *
     * @return The accessor
     */
    public ExecutableElement getAccessor() {
        return this.accessor;
    }

    public ExecutableElement getAccessorWrapper() {
        return this.accessor;
    }

    /**
     * Get the method representing the mutator, which or may not exist.
     *
     * @return The mutator
     */
    public Optional<ExecutableElement> getMutator() {
        return this.mutator;
    }

    /**
     * Tests whether this property's type is the least specific version used in the
     * interface hierarchy.
     *
     * @return True if tis property's type is the least specific
     */
    public boolean isLeastSpecificType(final Types types) {
        return types.isSameType(this.type, this.leastSpecificMethod.getReturnType());
    }

    public boolean isMostSpecificType(final Types types) {
        return types.isSameType(this.type, this.mostSpecificMethod.getReturnType());
    }

    @Override
    public int compareTo(final Property other) {
        return this.getName().compareTo(other.getName());
    }
}
