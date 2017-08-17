package ru.fomenkov.telemetry;

import ru.fomenkov.util.Log;

public class Telemetry {

    public Telemetry message(String format, Object... args) {
        Log.d("M" + format, args);
        return this;
    }

    public Telemetry warn(String format, Object... args) {
        Log.d("W" + format, args);
        return this;
    }

    public Telemetry error(String format, Object... args) {
        Log.d("E" + format, args);
        return this;
    }

    public Telemetry green(String format, Object... args) {
        Log.d("G" + format, args);
        return this;
    }
}
