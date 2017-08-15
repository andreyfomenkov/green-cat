package ru.fomenkov.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CommandExecutor {

    public static List<String> exec(String cmd) {
        List<String> lines = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            InputStream err = process.getErrorStream();
            InputStream in = process.getInputStream();
            BufferedReader errReader = new BufferedReader(new InputStreamReader(err));
            BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
            String line;

            while ((line = errReader.readLine()) != null) {
                lines.add(line);
            }

            while ((line = inReader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    public static void execNoOutput(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
