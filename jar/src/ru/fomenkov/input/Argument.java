package ru.fomenkov.input;

public enum Argument {

    PROJECT_PATH("-d"),
    CLASSPATH("-cp"),
    ANDROID_SDK_PATH("-sdk"),
    PACKAGE("-p"),
    MAIN_ACTIVITY("-a");

    private final String value;

    Argument(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
