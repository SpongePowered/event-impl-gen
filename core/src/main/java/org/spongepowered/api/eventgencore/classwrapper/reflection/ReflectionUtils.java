package org.spongepowered.api.eventgencore.classwrapper.reflection;

import org.spongepowered.api.eventgencore.annotation.ImplementedBy;

import java.util.ArrayDeque;
import java.util.Queue;

public class ReflectionUtils {

    public static ReflectionClassWrapper getBaseClass(Class<?> target, Class<?> annotation) {
        ImplementedBy implementedBy = null;
        final Queue<Class<?>> queue = new ArrayDeque<Class<?>>();

        queue.add(target);
        Class<?> scannedType;

        while ((scannedType = queue.poll()) != null) {
            if ((implementedBy = scannedType.getAnnotation(ImplementedBy.class)) != null) {
                break;
            }
            for (Class<?> implInterfaces : scannedType.getInterfaces()) {
                queue.offer(implInterfaces);
            }
        }

        if (implementedBy != null) {
            return new ReflectionClassWrapper(implementedBy.value());
        }
        throw new RuntimeException("Congratulations. You've attempted to create an event which doesn't have "
                + "an @ImplementedBy annotation, on itself of any of its superinterfaces. "
                + "If your event extends Event, then, well, something's mess up pretty badly. "
                + "If not: *WHY* aren't you extending Event????");
    }

}
