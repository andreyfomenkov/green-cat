package ru.fomenkov.telemetry;

import ru.fomenkov.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Telemetry {

    private final List<String> history = new ArrayList<>();

    private void log(String format, Object... args) {
        history.add(String.format(format, args));
    }

    public Telemetry message(String format, Object... args) {
        log("M" + format, args);
        return this;
    }

    public Telemetry warn(String format, Object... args) {
        log("W" + format, args);
        return this;
    }

    public Telemetry error(String format, Object... args) {
        log("E" + format, args);
        return this;
    }

    public Telemetry green(String format, Object... args) {
        log("G" + format, args);
        return this;
    }

    public void print() {
        for (String message : history) {
            Log.d(message);
        }
        history.clear();
    }
}
