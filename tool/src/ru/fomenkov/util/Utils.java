package ru.fomenkov.util;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;

import java.io.File;
import java.util.List;

public class Utils {

    public static String formatNanoTimeToSeconds(long nanoTime) {
        String format = String.format("%d", nanoTime / (long) Math.pow(10, 9));
        return format.endsWith(".0") ? format.substring(0, 1) : format;
    }

    public static List<String> findFilesRecursive(String path, String pattern) {
        String cmd = CommandLineBuilder.create("find")
                .add(new Parameter(path))
                .add(new Parameter("-name", pattern))
                .build();
        return CommandExecutor.execOnInputStream(cmd);
    }

    public static boolean fileExists(String path) {
        return new File(path).exists();
    }
}
