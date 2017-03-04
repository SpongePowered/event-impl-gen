package org.spongepowered.eventimplgen;

import com.google.common.base.Throwables;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * When using event-impl-gen for plugins, the source for the event interfaces
 * included in SpongeAPI isn't available. Spoon relies on reflection to provide
 * a "shadow" class structure in these cases.
 *
 * <p>Currently, Spoon does not support getting annotation values from these
 * elements. This class implements a simple way to load the annotation values
 * using reflection. In the future, it would be preferable to analyse the
 * binary classes using ASM instead of classloading them in the Gradle process.</p>
 */
final class ShadowSpoon {

    private ShadowSpoon() {
    }

    @SuppressWarnings("unchecked")
    static <T> T getAnnotationValue(CtAnnotation<?> annotationElement, String key) {
        try {
            Annotation annotation = loadAnnotation(annotationElement);
            return (T) annotation.getClass().getMethod(key).invoke(annotation);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    static CtTypeReference<?> getAnnotationTypeReference(CtAnnotation<?> annotationElement, String key) {
        try {
            Annotation annotation = loadAnnotation(annotationElement);
            Class<?> reference = (Class<?>) annotation.getClass().getMethod(key).invoke(annotation);
            return annotationElement.getFactory().createCtTypeReference(reference);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static Annotation loadAnnotation(CtAnnotation<?> annotationElement) throws Exception {
        Annotation annotation = (Annotation) annotationElement.getMetadata("annotation");
        if (annotation == null) {
            CtTypeReference<? extends Annotation> type = annotationElement.getAnnotationType();

            // Due to a bug in Spoon, it does not always set the correct
            // factory for the generated elements. This will cause the wrong
            // classpath to be used.
            type.setFactory(annotationElement.getParent().getFactory());

            annotation = findAnnotation(annotationElement.getParent(), type.getActualClass());
            annotationElement.putMetadata("annotation", annotation);
        }

        return annotation;
    }

    private static Annotation findAnnotation(CtElement parent, Class<? extends Annotation> annotationClass) throws Exception {
        if (parent instanceof CtMethod) {
            Class<?> clazz = ((CtType<?>) parent.getParent()).getActualClass();
            Method method = clazz.getMethod(((CtMethod<?>) parent).getSimpleName(), ((CtMethod<?>) parent).getParameters().stream()
                    .map(p -> p.getType().getActualClass())
                    .toArray(Class<?>[]::new));
            return method.getAnnotation(annotationClass);
        } else if (parent instanceof CtType) {
            return ((CtType<?>) parent).getActualClass().getAnnotation(annotationClass);
        }

        throw new UnsupportedOperationException();
    }

}
