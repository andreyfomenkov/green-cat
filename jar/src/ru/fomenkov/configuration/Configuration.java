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
    public String get(Property property, @NotNull String defaultValue) {
        return properties.getOrDefault(property, defaultValue);
    }

    public void remove(Property property) {
        properties.remove(property);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder("Properties list:\n");

        for (Property property : properties.keySet()) {
            String value = properties.get(property);
            builder.append(property).append(" = ").append(value).append("\n");
        }

        return builder.toString();
    }
}