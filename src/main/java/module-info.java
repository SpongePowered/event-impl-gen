@SuppressWarnings("requires-transitive-automatic")
module org.spongepowered.eventimplgen {
    exports org.spongepowered.eventimplgen.processor;
    exports org.spongepowered.eventimplgen.factory;
    exports org.spongepowered.eventimplgen.factory.plugin;
    exports org.spongepowered.eventimplgen.eventgencore;
    exports org.spongepowered.eventimplgen.signature;


    requires transitive dagger;
    requires transitive javax.inject;
    requires jakarta.inject;
    requires transitive java.compiler;
    requires transitive jdk.compiler;
    requires transitive org.spongepowered.eventimplgen.annotations;

    requires static transitive com.google.auto.service;
    requires static transitive org.jetbrains.annotations;
    requires com.palantir.javapoet;

    provides javax.annotation.processing.Processor with org.spongepowered.eventimplgen.processor.EventImplGenProcessor;
}