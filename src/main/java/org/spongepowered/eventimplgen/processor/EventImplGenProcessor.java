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

import com.google.auto.service.AutoService;
import org.spongepowered.api.util.annotation.eventgen.FactoryMethod;
import org.spongepowered.api.util.annotation.eventgen.GenerateFactoryMethod;
import org.spongepowered.eventimplgen.eventgencore.PropertySearchStrategy;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

/**
 * The entry point for the generator, starting as an AP.
 */
@AutoService(Processor.class)
@SupportedOptions({
    EventGenOptions.GENERATED_EVENT_FACTORY,
    EventGenOptions.SORT_PRIORITY_PREFIX,
    EventGenOptions.GROUPING_PREFIXES,
    EventGenOptions.INCLUSIVE_ANNOTATIONS,
    EventGenOptions.EXCLUSIVE_ANNOTATIONS
})
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
    public Set<String> getSupportedAnnotationTypes() {
        return this.component.options().inclusiveAnnotations();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported(); // todo: limit to a max tested version
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        final EventGenerationFilter filter = this.component.filter();
        final EventImplWriter writer = this.component.writer();
        final PropertySearchStrategy strategy = this.component.strategy();

        final Queue<Element> elements = new ArrayDeque<>(roundEnv.getElementsAnnotatedWith(GenerateFactoryMethod.class));
        Element active;
        while ((active = elements.poll()) != null) {
            if (active.getKind() == ElementKind.PACKAGE) {
                elements.addAll(active.getEnclosedElements());
            } else if (active.getKind().isInterface()) {
                final TypeElement event = (TypeElement) active;
               if (filter.test(event)) {
                   writer.propertyFound(event, strategy.findProperties(event));
                   writer.forwardedMethods(this.findForwardedMethods(event));
               }
            } else {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "This element was annotated but it is not a package or interface!", active);
            }
        }

        // If this is the last round, then let's do the actual generation
        if (roundEnv.processingOver() && !roundEnv.errorRaised()) {
            try {
                writer.dumpClasses();
            } catch (final IOException ex) {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Failed to write class information due to an exception: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        // Never claim annotations -- that way we don't block other processors from visiting them if they want to
        return false;
    }

    private List<ExecutableElement> findForwardedMethods(final TypeElement event) {
        final List<ExecutableElement> methods = new ArrayList<>();
        for (final ExecutableElement method : ElementFilter.methodsIn(event.getEnclosedElements())) {
            if (method.getModifiers().contains(Modifier.STATIC)
                && method.getAnnotation(FactoryMethod.class) != null) {
                methods.add(method);
            }
        }
        return methods;
    }
}
