package ru.fomenkov.configuration;

import com.sun.istack.internal.NotNull;
import org.apache.commons.io.FileUtils;
import ru.fomenkov.exception.ConfigurationParsingException;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigurationReader {

    private final String path;

    public ConfigurationReader(String path) {
        this.path = path;
    }

    @NotNull
    public Configuration read() throws IOException, ConfigurationParsingException {
        Configuration configuration = new Configuration();
        List<String> lines = FileUtils.readLines(new File(path));
        int lineNumber = 0;

        for (String line : lines) {
            lineNumber++;

            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.contains("=")) {
                int index = line.indexOf("=");
                String propertyName = line.substring(0, index).trim();
                String propertyValue = line.substring(index + 1).trim();

                try {
                    Property property = Property.valueOf(propertyName);
                    configuration.set(property, propertyValue);
                } catch (IllegalArgumentException e) {
                    throw new ConfigurationParsingException("Unknown property: " + propertyName);
                }
            } else {
                throw new ConfigurationParsingException("Corrupt property at line " + lineNumber);
            }
        }

        return configuration;
    }
}
