package ru.fomenkov;

import ru.fomenkov.input.LibraryInput;
import ru.fomenkov.input.LibraryInputReader;
import ru.fomenkov.exception.MissingArgumentsException;
import ru.fomenkov.util.Log;

public class Main {

    public static void main(String[] args) {
        Log.d("Starting GreenCat...");
        LibraryInputReader reader = new LibraryInputReader(args);
        LibraryInput input;

        try {
            input = reader.read();
        } catch (MissingArgumentsException ignore) {
            return;
        }

        input.setClasspath("sadasd");
    }
}
