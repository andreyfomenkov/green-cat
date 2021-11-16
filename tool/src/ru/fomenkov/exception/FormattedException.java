package ru.fomenkov.exception;

public abstract class FormattedException extends Exception {

    public FormattedException(String format, Object... args) {
        super(String.format(format, args));
    }

    public FormattedException(Throwable e, String format, Object... args) {
        super(String.format(format, args), e);
    }
}
