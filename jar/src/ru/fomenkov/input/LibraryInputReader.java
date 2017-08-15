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
        requiredArguments.add(Argument.PROJECT_PATH);
        requiredArguments.add(Argument.CLASSPATH);
        requiredArguments.add(Argument.ANDROID_SDK_PATH);
    }

    public LibraryInput read() throws MissingArgumentsException {
        LibraryInput input = new LibraryInput();
        Argument nextArg = null;

        for (String arg : args) {
            if (Argument.PROJECT_PATH.value().equals(arg)) {
                nextArg = Argument.PROJECT_PATH;

            } else if (Argument.CLASSPATH.value().equals(arg)) {
                nextArg = Argument.CLASSPATH;

            } else if (Argument.ANDROID_SDK_PATH.value().equals(arg)) {
                nextArg = Argument.ANDROID_SDK_PATH;
            } else {
                if (nextArg == null) {
                    Log.e("Invalid argument found: %s", arg);
                    throw new MissingArgumentsException("Invalid argument");
                } else {
                    switch (nextArg) {
                        case PROJECT_PATH:
                            input.setProjectPath(arg);
                            break;
                        case CLASSPATH:
                            input.setClasspath(arg);
                            break;
                        case ANDROID_SDK_PATH:
                            input.setAndroidSdkPath(arg);
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
