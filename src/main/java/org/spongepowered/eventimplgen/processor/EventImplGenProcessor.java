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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

/**
 * The entry point for the generator, starting as an AP.
 */
@AutoService(Processor.class)
@SupportedOptions({
    EventGenOptions.GENERATED_EVENT_FACTORY,
    EventGenOptions.INCLUSIVE_FOLDERS,
    EventGenOptions.EXCLUSIVE_FOLDERS,
    EventGenOptions.SORT_PRIORITY_PREFIX,
    EventGenOptions.GROUPING_PREFIXES,
    EventGenOptions.INCLUSIVE_ANNOTATIONS,
    EventGenOptions.EXCLUSIVE_ANNOTATIONS,
    EventGenOptions.DEBUG,
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes({
    "org.spongepowered.eventgen.annotations.AbsoluteSortPosition",
    "org.spongepowered.eventgen.annotations.DefaultImplemented",
    "org.spongepowered.eventgen.annotations.GenerateFactoryMethod",
    "org.spongepowered.eventgen.annotations.NoFactoryMethod",
    "org.spongepowered.eventgen.annotations.PropertySettings",
    "org.spongepowered.eventgen.annotations.ToStringExclude",
    "org.spongepowered.eventgen.annotations.TransformResult",
    "org.spongepowered.eventgen.annotations.TransformWith",
    "org.spongepowered.eventgen.annotations.UseField",
    "org.gradle.annotation.processing.isolating"
})
public class EventImplGenProcessor extends AbstractProcessor {

    public EventImplGenProcessor() {
    }

    private EventGenComponent component;

    static Element topLevelType(final TypeElement element) {
        Element output = element;
        while (output.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            output = output.getEnclosingElement();
        }
        return output;
    }

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.component = DaggerEventGenComponent.builder()
            .processorEnvironmentModule(new ProcessorEnvironmentModule(processingEnv))
            .build();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (!this.component.options().validate()) {
            return false;
        }

        final EventScanner scanner = this.component.scanner();
        final EventImplWriter writer = this.component.writer();

        if (!scanner.scanRound(roundEnv, writer, annotations)) {
            writer.skipRound();
        }

        try {
            writer.dumpRound(roundEnv.getRootElements());
            // If this is the last round, then let's do the actual generation
            if (roundEnv.processingOver()) {
               writer.dumpFinal();
            }
        } catch (final IOException ex) {
            this.processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, "Failed to write class information due to an exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        // Never claim annotations -- that way we don't block other processors from visiting them if they want to
        return false;
    }

}
