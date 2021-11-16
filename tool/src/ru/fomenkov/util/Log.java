package ru.fomenkov.util;

public class Log {

    private Log() {
    }

    public static void d(String format, Object... args) {
        String message = String.format(format, args);
        System.out.println(message);
    }

    public static void e(String format, Object... args) {
        String message = String.format(format, args);
        System.err.println(message);
    }
}
