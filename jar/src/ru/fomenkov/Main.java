package ru.fomenkov;

import ru.fomenkov.exception.MissingArgumentsException;
import ru.fomenkov.input.LibraryInput;
import ru.fomenkov.input.LibraryInputReader;
import ru.fomenkov.message.ProjectSetup;
import ru.fomenkov.task.TaskExecutor;
import ru.fomenkov.task.cleanup.CleanupTask;
import ru.fomenkov.task.deploy.DeployTask;
import ru.fomenkov.task.dex.DexTask;
import ru.fomenkov.task.diff.GitDiff;
import ru.fomenkov.task.javac.CompileWithJavac;
import ru.fomenkov.task.restart.RestartAppTask;
import ru.fomenkov.task.retrolambda.RetrolambdaTask;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;

public class Main {

    public static void main(String[] args) {
        LibraryInputReader reader = new LibraryInputReader(args);
        LibraryInput input;

        try {
            input = reader.read();
        } catch (MissingArgumentsException ignore) {
            return;
        }

        String classpath = input.getClasspath();
        String projectPath = input.getProjectPath();
        String androidSdkPath = input.getAndroidSdkPath();
        String packageName = input.getPackageName();
        String mainActivity = input.getMainActivity();
        String deployPath = GreenCat.getDexDeployPath();
        File compileDir = GreenCat.getCompileDir(projectPath);
        File lambdaDir = GreenCat.getLambdaDir(projectPath);
        File dexDir = GreenCat.getDexBuildDir(projectPath);

        Telemetry telemetry = new Telemetry();
        ProjectSetup launchMessage = new ProjectSetup(projectPath);
        TaskExecutor executor = TaskExecutor.create(launchMessage, telemetry)
                .add(new CleanupTask())
                .add(new GitDiff())
                .add(new CompileWithJavac(projectPath, classpath, compileDir))
                .add(new RetrolambdaTask(compileDir.getAbsolutePath(), lambdaDir.getAbsolutePath()))
                .add(new DexTask(androidSdkPath, lambdaDir, dexDir))
                .add(new DeployTask(androidSdkPath, dexDir.getAbsolutePath(), deployPath))
                .add(new RestartAppTask(androidSdkPath, packageName, mainActivity));

        TaskExecutor.Result result = executor.execute();

        switch (result.status) {
            case SUCCESS:
                telemetry.green("Deploying complete");
                break;
            case TERMINATED:
                telemetry.warn("Deploying terminated");
                break;
            case ERROR:
                telemetry.error("Deploying failed");
                break;
        }
    }
}
