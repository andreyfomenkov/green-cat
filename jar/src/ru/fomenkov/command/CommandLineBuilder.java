package ru.fomenkov.command;

import java.util.ArrayList;
import java.util.List;

public class CommandLineBuilder {

    private final String command;
    private List<Parameter> parameters = new ArrayList<>();

    public static CommandLineBuilder create(String command) {
        return new CommandLineBuilder(command);
    }

    private CommandLineBuilder(String command) {
        this.command = command;
    }

    public CommandLineBuilder add(Parameter parameter) {
        parameters.add(parameter);
        return this;
    }

    public String build() {
        StringBuilder builder = new StringBuilder(command);

        for (Parameter parameter : parameters) {
            builder.append(" ").append(parameter.composeString());
        }

        return builder.toString();
    }
}
