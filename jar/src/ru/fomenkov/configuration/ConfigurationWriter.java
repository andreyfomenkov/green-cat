package ru.fomenkov.configuration;

import com.sun.istack.internal.NotNull;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationWriter {

    private final String path;

    public ConfigurationWriter(String path) {
        this.path = path;
    }

    public boolean write(@NotNull Configuration configuration) {
        List<String> lines = new ArrayList<>();

        for (Property property : configuration.properties.keySet()) {
            lines.add(property + "=" + configuration.get(property, ""));
        }

        try {
            FileUtils.writeLines(new File(path), lines);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
