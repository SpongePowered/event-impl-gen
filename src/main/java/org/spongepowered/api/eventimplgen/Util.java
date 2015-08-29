package org.spongepowered.api.eventimplgen;

import org.gradle.api.file.FileCollection;
import spoon.support.processing.XmlProcessorProperties;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

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

    public static String[] toStringArray(FileCollection fileCollection) {
        final Set<File> files = fileCollection.getFiles();
        final String[] strings = new String[files.size()];
        int i = 0;
        for (File file : files) {
            strings[i++] = file.getAbsolutePath();
        }
        return strings;
    }

}
