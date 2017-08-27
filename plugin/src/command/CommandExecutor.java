package command;

import ui.window.TelemetryToolWindow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CommandExecutor {

    private static final long WINDOW_UPDATE_INTERVAL = 1000;

    public static List<String> execOnInputStream(TelemetryToolWindow window, String cmd) {
        return exec(window, false, cmd);
    }

    public static List<String> execOnErrorStream(TelemetryToolWindow window, String cmd) {
        return exec(window, true, cmd);
    }

    private static List<String> exec(TelemetryToolWindow window, boolean errorStream, String cmd) {
        List<String> lines = new ArrayList<>();

        try {
            Process process = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", cmd });
            InputStream in = errorStream ? process.getErrorStream() : process.getInputStream();
            BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
            String line;

            while ((line = inReader.readLine()) != null) {
                lines.add(line);

                if (line.startsWith("M")) {
                    window.message(line.substring(1));
                } else if (line.startsWith("E")) {
                    window.error(line.substring(1));
                } else if (line.startsWith("W")) {
                    window.warn(line.substring(1));
                } else if (line.startsWith("G")) {
                    window.green(line.substring(1));
                } else {
                    window.message(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        window.update();
        return lines;
    }
}
