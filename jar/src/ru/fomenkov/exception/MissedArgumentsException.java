package ru.fomenkov.exception;

public class MissedArgumentsException extends FormattedException {

    public MissedArgumentsException(String format, Object... args) {
        super(format, args);
    }

    public MissedArgumentsException(Throwable e, String format, Object... args) {
        super(e, format, args);
    }
}
