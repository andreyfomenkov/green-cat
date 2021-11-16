package ru.fomenkov.exception;

public class ModuleFileParsingException extends FormattedException {

    public ModuleFileParsingException(String format, Object... args) {
        super(format, args);
    }

    public ModuleFileParsingException(Throwable e, String format, Object... args) {
        super(e, format, args);
    }
}
