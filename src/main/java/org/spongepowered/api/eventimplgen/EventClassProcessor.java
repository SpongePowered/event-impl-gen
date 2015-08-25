/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015-2015 SpongePowered <http://spongepowered.org/>
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

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtInterface;

import java.util.Arrays;
import java.util.List;

public class EventClassProcessor extends AbstractProcessor<CtInterface<?>> {

    private static final String EVENT_PACKAGE = "org.spongepowered.api.event";
    private static final List<String> BASE_EVENTS = Arrays.asList("Event", "GameEvent");

    @Override
    public boolean isToBeProcessed(CtInterface<?> candidate) {
        /*

            Allow:
                - org.spongepowered.api.event.Event
                - org.spongepowered.api.event.GameEvent
                - org.spongepowered.api.event.action.*
                - org.spongepowered.api.event.inventory.*
                - org.spongepowered.api.event.source.*
                - org.spongepowered.api.event.target.*

        */
        final String name = candidate.getQualifiedName();
        final boolean isInEventPackage = name.startsWith(EVENT_PACKAGE);
        if (!isInEventPackage) {
            return false;
        }
        final boolean isCause = name.startsWith("cause", EVENT_PACKAGE.length() + 1);
        if (isCause) {
            return false;
        }
        final boolean isInRoot = name.indexOf('.', EVENT_PACKAGE.length() + 1) < 0;
        final boolean isBaseEvent = BASE_EVENTS.contains(candidate.getSimpleName());
        return !isInRoot || isBaseEvent;
    }

    @Override
    public void process(CtInterface<?> element) {
        System.out.println(element.getQualifiedName());
    }
}
