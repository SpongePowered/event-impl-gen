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
package org.spongepowered.eventimplgen.factory.plugin;

import org.objectweb.asm.ClassWriter;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.factory.EventImplClassWriter;

import javax.lang.model.element.TypeElement;

/**
 * Represents a class which modifies the behavior of an event generator.
 */
public interface EventFactoryPlugin {

    /**
     * Called to allow a plugin to override the generation of the field and method(s) for a {@link Property}.
     *
     * <p>If an event factory plugin does not have any special behavior for the provided {@link Property}, it
     * should return {@code false}, which passes on the property to other plugins for processing.</p>
     *
     * <p>Returning {@code true} stops the processing of the provided {@link Property}.</p>
     *
     * @param eventClass The {@link Class} of the event an implementation is being generated for
     * @param internalName The internal name of the event
     * @param classWriter The {@link ClassWriter} being used to generate the event class implementation
     * @param property The {@link Property} being processed
     *
     * @return whether the provided {@link Property} was processed.
     */
    Result contributeProperty(TypeElement eventClass, String internalName, EventImplClassWriter classWriter, Property property);

    enum Result {
        /**
         * Claim the property, having successfully processed it.
         */
        SUCCESSS,
        /**
         * Indicate that this plugin does not claim the provided property.
         */
        IGNORE,
        /**
         * Indicate that an error occurred while processing this property.
         *
         * <p>This should be accompanied by a logged error message.</p>
         *
         * <p>By returning this value, final writing of a class file will be
         * suppressed but other properties in the class may be processed.</p>
         */
        FAILURE;
    }

}
