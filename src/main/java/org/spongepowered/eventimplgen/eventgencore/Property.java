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
import spoon.reflect.reference.CtTypeReference;

import java.util.Objects;
import java.util.Optional;

/**
 * A property is a getter with possibly a setter pair.
 *
 */
public final class Property implements Comparable<Property> {

    private final String name;
    private final CtTypeReference<?> type;
    private final CtMethod<?> leastSpecificMethod;
    private final CtMethod<?> mostSpecificMethod;
    private final CtMethod<?> accessor;
    private final Optional<CtMethod<?>> mutator;

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
        final CtTypeReference<?> type,
        final CtMethod<?> leastSpecificMethod,
        final CtMethod<?> mostSpecificMethod,
        final CtMethod<?> accessor,
        final @Nullable CtMethod<?> mutator
   ) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(leastSpecificMethod, "leastSpecificMethod");
        Objects.requireNonNull(mostSpecificMethod, "mostSpecificMethod");
        Objects.requireNonNull(accessor, "accessor");
        this.name = name;
        this.type = type;
        this.leastSpecificMethod = leastSpecificMethod;
        this.mostSpecificMethod = mostSpecificMethod;
        this.accessor = accessor;
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
    public CtTypeReference<?> getType() {
        return this.type;
    }

    public CtTypeReference<?> getWrapperType() {
        return this.type;
    }

    /**
     * Gets the least specific version of the accessor used.
     *
     * @return The least specific accessor
     */
    public CtMethod<?> getLeastSpecificMethod() {
        return this.leastSpecificMethod;
    }

    /**
     * Gets the least specific version of the type used
     *
     * <p>This is used for the type of the generated field used to hold the value.</p>
     *
     * @return The type
     */
    public CtTypeReference<?> getLeastSpecificType() {
        return this.leastSpecificMethod.getType();
    }

    /**
     * Get the most specific version of the accessor used.
     *
     * @return The least specific accessor
     */
    public CtMethod<?> getMostSpecificMethod() {
        return this.mostSpecificMethod;
    }

    /**
     * Gets the most specific version of the type used
     *
     * @return The type
     */
    public CtTypeReference<?> getMostSpecificType() {
        return this.mostSpecificMethod.getType();
    }

    /**
     * Get the method representing the accessor.
     *
     * @return The accessor
     */
    public CtMethod<?> getAccessor() {
        return this.accessor;
    }

    public CtMethod<?> getAccessorWrapper() {
        return this.accessor;
    }

    /**
     * Get the method representing the mutator, which or may not exist.
     *
     * @return The mutator
     */
    public Optional<CtMethod<?>> getMutator() {
        return this.mutator;
    }

    /**
     * Tests whether this property's type is the least specific version used in the
     * interface hierarchy.
     *
     * @return True if tis property's type is the least specific
     */
    public boolean isLeastSpecificType() {
        return this.type.getQualifiedName().equals(this.leastSpecificMethod.getType().getQualifiedName());
    }

    public boolean isMostSpecificType() {
        return this.type.getQualifiedName().equals(this.mostSpecificMethod.getType().getQualifiedName());
    }

    @Override
    public int compareTo(final Property other) {
        return this.getName().compareTo(other.getName());
    }
}
