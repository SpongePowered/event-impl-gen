package org.spongepowered.api.eventgencore.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
/**
 * Used to indicate the absolute position of a property when sorted.
 *
 * <p>A value of 0 indicates that a property would always be sorted first,
 * a value of 1 indicates that a property would always be sorted second, and so on.</p>
 *
 * <p>If a gap is left in the absolute ordering of properties, the
 * next-highest-numbered property will be placed next. For example,
 * properties with the absolute ordering 0, 1, and 3 will still be adjacent to
 * each other.</p>
 */
public @interface AbsoluteSortPosition {

    /**
     * Gets the absolute position for the annotated property
     *
     * @return the absolute position
     */
    int value();

}
