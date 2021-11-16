package ru.fomenkov.exception;

public class ClasspathException extends Exception {

    public ClasspathException(String message, Throwable e) {
        super(message, e);
    }

    public ClasspathException(String message) {
        super(message);
    }
}
