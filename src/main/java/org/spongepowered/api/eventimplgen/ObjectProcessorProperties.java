package org.spongepowered.api.eventimplgen;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import spoon.processing.ProcessorProperties;

import java.util.Map;
import java.util.NoSuchElementException;

public class ObjectProcessorProperties implements ProcessorProperties {

    private final Map<String, Object> properties = Maps.newHashMap();
    private final String processorName;

    public ObjectProcessorProperties(String processorName) {
        this.processorName = processorName;
    }

    public void put(String name, Object property) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(property, "property");
        properties.put(name, property);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Class<T> type, String name) {
        final Object property = properties.get(name);
        if (property == null) {
            throw new NoSuchElementException(name);
        }
        if (!type.isInstance(property)) {
            throw new ClassCastException(property.getClass().getCanonicalName() + " to " + type.getCanonicalName());
        }
        return (T) property;
    }

    @Override
    public String getProcessorName() {
        return this.processorName;
    }

}
