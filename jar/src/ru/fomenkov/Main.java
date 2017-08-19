package ru.fomenkov;

import ru.fomenkov.exception.MissedArgumentsException;
import ru.fomenkov.input.LibraryInput;
import ru.fomenkov.input.LibraryInputReader;
import ru.fomenkov.message.ProjectSetup;
import ru.fomenkov.task.TaskExecutor;
import ru.fomenkov.task.diff.GitDiffTask;
import ru.fomenkov.task.resolve.ModulesResolveTask;
import ru.fomenkov.task.setup.ProjectSetupTask;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;

public class Main {

    public static void main(String[] args) {
        LibraryInputReader reader = new LibraryInputReader(args);
        LibraryInput input;

        try {
            input = reader.read();
        } catch (MissedArgumentsException ignore) {
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
                .add(new ProjectSetupTask())
                .add(new ModulesResolveTask())
                .add(new GitDiffTask());
//                .add(new CompileWithJavac(projectPath, classpath, compileDir))
//                .add(new RetrolambdaTask(compileDir.getAbsolutePath(), lambdaDir.getAbsolutePath()))
//                .add(new DexTask(androidSdkPath, lambdaDir, dexDir))
//                .add(new DeployTask(androidSdkPath, dexDir.getAbsolutePath(), deployPath))
//                .add(new RestartAppTask(androidSdkPath, packageName, mainActivity));

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
