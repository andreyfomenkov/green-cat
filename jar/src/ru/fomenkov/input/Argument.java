package ru.fomenkov.input;

public enum Argument {

    PROJECT_PATH("-d"),
    PROJECT_MODULES("-m"),
    CLASSPATH("-cp"),
    ANDROID_SDK_PATH("-sdk");

    private final String value;

    Argument(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
