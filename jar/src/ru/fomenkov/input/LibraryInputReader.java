package ru.fomenkov.input;

import ru.fomenkov.exception.MissingArgumentsException;
import ru.fomenkov.util.Log;

import java.util.HashSet;
import java.util.Set;

public class LibraryInputReader {

    private final Set<Argument> requiredArguments = new HashSet<>();
    private final String[] args;

    public LibraryInputReader(String[] args) {
        this.args = args;
        requiredArguments.add(Argument.PROJECT_DIR);
        requiredArguments.add(Argument.CLASSPATH);
    }

    public LibraryInput read() throws MissingArgumentsException {
        LibraryInput input = new LibraryInput();
        Argument nextArg = null;

        for (String arg : args) {
            if (Argument.PROJECT_DIR.value().equals(arg)) {
                nextArg = Argument.PROJECT_DIR;
            } else if (Argument.CLASSPATH.value().equals(arg)) {
                nextArg = Argument.CLASSPATH;
            } else {
                if (nextArg == null) {
                    Log.e("Invalid argument found: %s", arg);
                    throw new MissingArgumentsException("Invalid argument");
                } else {
                    switch (nextArg) {
                        case PROJECT_DIR:
                            input.setProjectDir(arg);
                            break;
                        case CLASSPATH:
                            input.setClasspath(arg);
                            break;
                    }
                    nextArg = null;
                }
            }

            if (nextArg != null && !requiredArguments.remove(nextArg)) {
                Log.e("Duplicate argument found: %s", nextArg.value());
                throw new MissingArgumentsException("Duplicate argument found");
            }
        }

        if (requiredArguments.size() > 0) {
            StringBuilder builder = new StringBuilder();

            for (Argument arg : requiredArguments) {
                builder.append(arg).append(" ");
            }

            Log.e("Missing required arguments: %s", builder);
            throw new MissingArgumentsException("Missing some input arguments");
        }

        return input;
    }
}
