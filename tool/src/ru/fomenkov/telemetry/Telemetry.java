package ru.fomenkov.telemetry;

public class Telemetry {

    private Telemetry() {
    }

    public static void log(String format, Object... args) {
        System.out.printf((format) + "%n", args);
    }

    public static void err(String format, Object... args) {
        System.err.printf((format) + "%n", args);
    }
}
