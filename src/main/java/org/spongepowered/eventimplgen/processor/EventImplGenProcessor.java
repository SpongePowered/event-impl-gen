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
package org.spongepowered.eventimplgen.processor;


import org.spongepowered.eventimplgen.EventInterfaceProcessor;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public class EventImplGenProcessor extends AbstractProcessor {

    private EventGenComponent component;

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.component = DaggerEventGenComponent.builder()
            .processorEnvironmentModule(new ProcessorEnvironmentModule(processingEnv))
            .build();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        final EventInterfaceProcessor processor = new EventInterfaceProcessor(getSource(), this.inclusiveAnnotations, this.exclusiveAnnotations);
        final Map<TypeElement, List<Property>> foundProperties = processor.getFoundProperties();
        final List<ExecutableElement> forwardedMethods = processor.getForwardedMethods();
        this.foundProperties.put(event, this.strategy.findProperties(event));
        this.forwardedMethods.addAll(this.findForwardedMethods(event));

        this.sorter = new PropertySorter(this.sortPriorityPrefix, this.groupingPrefixes);

        dumpClasses(foundProperties, forwardedMethods);

        if (roundEnv.processingOver() && !roundEnv.errorRaised()) {

        }

        // Never claim annotations -- that way we don't block other processors from visiting them if they want to
        return false;
    }
}
