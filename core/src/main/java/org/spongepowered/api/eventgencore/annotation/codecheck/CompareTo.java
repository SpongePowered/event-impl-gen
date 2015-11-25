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
package org.spongepowered.api.eventgencore.annotation.codecheck;

/**
 * Indicates that the return value of the annotated method should be compared
 * (using ==) to the return value of the method with the corresponding
 * {@link CompareTo} annotation.
 */
public @interface CompareTo {

    /**
     * Gets the name used to match this annotation to its corresponding {@link CompareTo}
     * annotation.
     *
     * <p>Changing this is only necessary when multiple {@link CompareTo} annotations are
     * present in the event interface or on a return type.
     *
     * @return The name to use
     */
    String value() default "";

    /**
     * Gets the method to call on the annotated method's return type.
     *
     * <p>By default, no method is called.</p>
     *
     * @return The method to call
     */
    String method() default "";

    /**
     * Indicates in which position in the '==' comparison the annotated
     * method's return value will be in.
     *
     * @return The position
     */
    int position();

}
