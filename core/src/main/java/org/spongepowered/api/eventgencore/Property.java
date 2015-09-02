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
import org.spongepowered.api.eventgencore.classwrapper.ClassWrapper;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;

import javax.annotation.Nullable;

/**
 * A property is a getter with possibly a setter pair.
 *
 * @param <T> The class type to use
 * @param <M> The method type to use
 */
public final class Property<T, M> implements Comparable<Property<T, M>> {

    private final String name;
    private final ClassWrapper<T, M> type;
    private final MethodWrapper<T, M> leastSpecificMethod;
    private final MethodWrapper<T, M> mostSpecificMethod;
    private final MethodWrapper<T, M> accessor;
    private final Optional<MethodWrapper<T, M>> mutator;

    /**
     * Create a new property.
     *
     * @param name The name of the property
     * @param type The type of property
     * @param leastSpecificMethod  The least specific method
     * @param accessor The accessor
     * @param mutator The mutator
     */
    public Property(String name, ClassWrapper<T, M> type, MethodWrapper<T, M> leastSpecificMethod, MethodWrapper<T, M> mostSpecificMethod, MethodWrapper<T, M> accessor, @Nullable MethodWrapper<T, M> mutator) {
        checkNotNull(name, "name");
        checkNotNull(type, "type");
        checkNotNull(leastSpecificMethod, "leastSpecificMethod");
        checkNotNull(mostSpecificMethod, "mostSpecificMethod");
        checkNotNull(accessor, "accessor");
        this.name = name;
        this.type = type;
        this.leastSpecificMethod = leastSpecificMethod;
        this.mostSpecificMethod = mostSpecificMethod;
        this.accessor = accessor;
        this.mutator = Optional.fromNullable(mutator);
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
    public T getType() {
        return this.type.getActualClass();
    }

    /**
     * Gets the least specific version of the accessor used.
     *
     * @return The least specific accessor
     */
    public M getLeastSpecificMethod() {
        return this.leastSpecificMethod.getActualMethod();
    }

    /**
     * Gets the least specific version of the type used
     *
     * <p>This is used for the type of the generated field used to hold the value.</p>
     *
     * @return The type
     */
    public T getLeastSpecificType() {
        return this.leastSpecificMethod.getReturnType().getActualClass();
    }

    /**
     * Get the most specific version of the accessor used.
     *
     * @return The least specific accessor
     */
    public M getMostSpecificMethod() {
        return this.mostSpecificMethod.getActualMethod();
    }

    /**
     * Gets the most specific version of the type used
     *
     * @return The type
     */
    public T getMostSpecificType() {
        return this.mostSpecificMethod.getReturnType().getActualClass();
    }

    /**
     * Get the method representing the accessor.
     *
     * @return The accessor
     */
    public M getAccessor() {
        return this.accessor.getActualMethod();
    }

    public MethodWrapper<T, M> getAccessorWrapper() {
        return this.accessor;
    }

    /**
     * Get the method representing the mutator, which or may not exist.
     *
     * @return The mutator
     */
    public Optional<M> getMutator() {
        if (this.mutator.isPresent()) {
            return Optional.of(this.mutator.get().getActualMethod());
        }
        return Optional.absent();
    }

    /**
     * Tests whether this property's type is the least specific version used in the
     * interface hierarchy.
     *
     * @return True if tis property's type is the least specific
     */
    public boolean isLeastSpecificType() {
        return this.type.getActualClass().equals(this.leastSpecificMethod.getReturnType().getActualClass());
    }

    public boolean isMostSpecificType() {
        return this.type.getActualClass().equals(this.mostSpecificMethod.getReturnType().getActualClass());
    }

    @Override
    public int compareTo(Property<T, M> otherProperty) {
        if (this.isSubtypeOf(this.accessor, otherProperty.accessor)) {
            if (this.isSubtypeOf(otherProperty.accessor, this.accessor)) {
                // Same class, so compare lexographically
                return this.accessor.getName().compareTo(otherProperty.accessor.getName());
            }
            // This is a subclass/subinterface, so it's greater (comes after)
            return 1;
        }
        // This is a superclass/superinterface, so it's lesser (comes before)
        return -1;
    }

    private boolean isSubtypeOf(MethodWrapper<T, M> first, MethodWrapper<T, M> second) {
        return first.getEnclosingClass().isSubtypeOf(second.getEnclosingClass());
    }
}
