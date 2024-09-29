module org.spongepowered.eventimplgen.annotations {
    exports org.spongepowered.eventgen.annotations;

    // This way we can still use the internal package, but not
    // to external developers.
    exports org.spongepowered.eventgen.annotations.internal to org.spongepowered.eventimplgen;
}