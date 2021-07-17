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

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
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
    EventGenOptions.EXCLUSIVE_ANNOTATIONS,
    EventGenOptions.DEBUG
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
        if (!this.component.options().validate()) {
            return false;
        }

        final EventScanner scanner = this.component.scanner();
        final EventImplWriter writer = this.component.writer();

        if (!scanner.scanRound(roundEnv, writer, annotations)) {
            writer.skipRound();
        }

        try {
            writer.dumpRound();
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
