package org.spongepowered.eventimplgen.processor;


import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

public class EventImplGenProcessor extends AbstractProcessor {

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

        // Never claim annotations -- that way we don't block other processors from visiting them if they want to
        return false;
    }
}
