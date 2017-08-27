package ru.fomenkov.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CommandExecutor {

    public static List<String> execOnInputStream(String cmd) {
        return exec(false, cmd);
    }

    public static List<String> execOnErrorStream(String cmd) {
        return exec(true, cmd);
    }

    private static List<String> exec(boolean errorStream, String cmd) {
        List<String> lines = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", cmd });
            InputStream in = errorStream ? process.getErrorStream() : process.getInputStream();
            BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
            String line;

            while ((line = inReader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }
}
