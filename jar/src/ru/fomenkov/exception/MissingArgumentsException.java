package ru.fomenkov.exception;

public class MissingArgumentsException extends FormattedException {

    public MissingArgumentsException(String format, Object... args) {
        super(format, args);
    }

    public MissingArgumentsException(Throwable e, String format, Object... args) {
        super(e, format, args);
    }
}
