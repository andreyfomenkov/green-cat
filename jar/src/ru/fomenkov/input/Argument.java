package ru.fomenkov.input;

public enum Argument {

    PROJECT_DIR("-projectdir"),
    CLASSPATH("-classpath");

    private final String value;

    Argument(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
