package ru.fomenkov.configuration;

import com.sun.istack.internal.NotNull;

import java.util.HashMap;
import java.util.Map;

public class Configuration {

    protected final Map<Property, String> properties = new HashMap<>();

    public void set(Property property, @NotNull String value) {
        properties.put(property, value);
    }

    @NotNull
    public String get(Property property) {
        String value = properties.get(property);

        if (value == null) {
            throw new IllegalStateException("No value found for property: " + property.name());
        }
        return value;
    }
}