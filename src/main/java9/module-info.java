module org.spongepowered.eventimplgen {
    exports org.spongepowered.eventimplgen.processor;

    requires java.compiler;
    requires com.squareup.javapoet;
    requires transitive dagger;
    requires transitive org.spongepowered.eventimplgen.annotations;

    requires static transitive com.google.auto.service;
    requires static transitive org.jetbrains.annotations;
    // requires static transitive javax.inject; TODO maybe?

    provides javax.annotation.processing.Processor with org.spongepowered.eventimplgen.processor.EventImplGenProcessor;
}