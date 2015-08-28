package org.spongepowered.api.eventimplgen;

import spoon.support.processing.XmlProcessorProperties;

import java.lang.reflect.Field;
import java.util.Map;

public class Util {

    @SuppressWarnings("unchecked")
    public static <T> T getProperty(XmlProcessorProperties properties, String name) {
        try {
            final Field propsField = properties.getClass().getDeclaredField("props");
            propsField.setAccessible(true);
            final Map<String, Object> props = (Map<String, Object>) propsField.get(properties);
            return (T) props.get(name);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

}
