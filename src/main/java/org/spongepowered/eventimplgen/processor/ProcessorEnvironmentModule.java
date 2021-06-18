package org.spongepowered.eventimplgen.processor;

import dagger.Module;
import dagger.Provides;

import java.util.Locale;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@Module
public abstract class ProcessorEnvironmentModule {
    private final ProcessingEnvironment environment;

    protected ProcessorEnvironmentModule(final ProcessingEnvironment environment) {
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
    Locale provideLocale(final ProcessingEnvironment environment) {
        return environment.getLocale();
    }


    // TODO: Expose processor options?


}
