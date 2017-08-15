package ru.fomenkov;

import ru.fomenkov.exception.MissingArgumentsException;
import ru.fomenkov.input.LibraryInput;
import ru.fomenkov.input.LibraryInputReader;
import ru.fomenkov.message.ProjectSetup;
import ru.fomenkov.task.TaskExecutor;
import ru.fomenkov.task.cleanup.CleanupTask;
import ru.fomenkov.task.diff.GitDiff;
import ru.fomenkov.task.javac.CompileWithJavac;
import ru.fomenkov.telemetry.Telemetry;
import ru.fomenkov.util.Log;

import java.io.File;

public class Main {

    public static void main(String[] args) {
        Log.d("Starting GreenCat...");
        Log.d(" ");
        LibraryInputReader reader = new LibraryInputReader(args);
        LibraryInput input;

        try {
            input = reader.read();
        } catch (MissingArgumentsException ignore) {
            return;
        }

        String projectPath = input.getProjectPath();
        String classpath = "";
        File objDir = GreenCat.getCompileDir(projectPath);

        Telemetry telemetry = new Telemetry();
        ProjectSetup launchMessage = new ProjectSetup(projectPath);
        TaskExecutor executor = TaskExecutor.create(launchMessage, telemetry)
                .add(new CleanupTask())
                .add(new GitDiff())
                .add(new CompileWithJavac(projectPath, classpath, objDir));

        TaskExecutor.Result result = executor.execute();
        telemetry.printOutput();

        Log.d(" ");
        switch (result.status) {
            case SUCCESS:
                Log.d("Deploying complete");
                break;
            case TERMINATED:
                Log.d("Deploying terminated");
                break;
            case ERROR:
                Log.e("Deploying failed");
                break;
        }
    }
}
