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

import dagger.Module;
import dagger.Provides;

import java.util.Locale;
import java.util.Map;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@Module
public class ProcessorEnvironmentModule {

    private final ProcessingEnvironment environment;

    public ProcessorEnvironmentModule(final ProcessingEnvironment environment) {
        this.environment = environment;
    }

    @Provides
    ProcessingEnvironment provideEnvironment() {
        return this.environment;
    }

    @Provides
    Types provideTypes(final ProcessingEnvironment environment) {
        return environment.getTypeUtils();
    }

    @Provides
    Elements provideElements(final ProcessingEnvironment environment) {
        return environment.getElementUtils();
    }

    @Provides
    Messager provideMessager(final ProcessingEnvironment environment) {
        return environment.getMessager();
    }

    @Provides
    Filer provideFiler(final ProcessingEnvironment environment) {
        return environment.getFiler();
    }

    @Provides
    SourceVersion provideSourceVersion(final ProcessingEnvironment environment) {
        return environment.getSourceVersion();
    }

    @Provides
    @PreviewFeatures
    boolean providePreviewFeatures(final ProcessingEnvironment environment) {
        return PreviewFeatureShim.previewFeaturesEnabled(environment);
    }

    @Provides
    Locale provideLocale(final ProcessingEnvironment environment) {
        return environment.getLocale();
    }

    @Provides
    @Singleton
    @ProcessorOptions
    Map<String, String> options(final ProcessingEnvironment environment) {
        return environment.getOptions();
    }

}
