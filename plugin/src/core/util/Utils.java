package core.util;

import core.command.CommandExecutor;
import core.command.CommandLineBuilder;
import core.command.Parameter;

import java.util.List;

public class Utils {

    public static String formatNanoTimeToSeconds(long nanoTime) {
        String format = String.format("%.1f", nanoTime / Math.pow(10, 9));
        return format.endsWith(".0") ? format.substring(0, 1) : format;
    }

    public static List<String> findFilesRecursive(String path, String pattern) {
        String cmd = CommandLineBuilder.create("find")
                .add(new Parameter(path))
                .add(new Parameter("-name", pattern))
                .build();
        return CommandExecutor.exec(cmd);
    }
}
