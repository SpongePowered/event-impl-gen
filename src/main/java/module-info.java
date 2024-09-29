module org.spongepowered.eventimplgen {
    exports org.spongepowered.eventimplgen.processor;

    requires dagger;
    requires javax.inject;
    requires jakarta.inject;
    requires io.soabase.java.composer;
    requires transitive java.compiler;
    requires transitive org.spongepowered.eventgen.annotations;

    requires static transitive com.google.auto.service;
    requires static transitive org.jetbrains.annotations;

    provides javax.annotation.processing.Processor with org.spongepowered.eventimplgen.processor.EventImplGenProcessor;
}