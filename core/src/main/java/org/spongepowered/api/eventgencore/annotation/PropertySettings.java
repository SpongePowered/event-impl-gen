package org.spongepowered.api.eventgencore.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to specify the settings used when generating code for a property.
 *
 * <p>This annotation should always be placed on the getter method of a property.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PropertySettings {

    /**
     * Indicates whether the annotated property is required to be passed in to the generated constructor.
     *
     * @return Whether the annotated property is required to be passed in to the generated constructor.
     */
    boolean requiredParameter() default true;

    /**
     * Indicates whether the annotated property should have methods generated for it.
     *
     * <p>If this value is set to <code>true</code>, {@link #requiredParameter()} can
     * only be set to <code>false</code> if the annotated property is a primitive.</p>
     *
     * @return Whether the annotated property should have methods generated for it.
     */
    boolean generateMethods() default true;

}
