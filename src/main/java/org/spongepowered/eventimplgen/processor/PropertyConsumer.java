package org.spongepowered.eventimplgen.processor;

import org.spongepowered.eventimplgen.eventgencore.Property;

import java.util.List;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A listener that accepts properties found from annotation processor rounds.
 */
public interface PropertyConsumer {

    void propertyFound(final TypeElement event, final List<Property> property);

    void forwardedMethods(final List<? extends ExecutableElement> elements);

}
