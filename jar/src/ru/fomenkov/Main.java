package ru.fomenkov;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.exception.MissedArgumentsException;
import ru.fomenkov.input.LibraryInput;
import ru.fomenkov.input.LibraryInputReader;
import ru.fomenkov.message.GitDiffMessage;
import ru.fomenkov.message.ProjectSetup;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.TaskExecutor;
import ru.fomenkov.task.diff.GitDiffTask;
import ru.fomenkov.task.resolve.ModulesResolveTask;
import ru.fomenkov.task.setup.ProjectSetupTask;
import ru.fomenkov.telemetry.Telemetry;
import ru.fomenkov.util.Log;
import ru.fomenkov.util.Utils;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        long startTime = System.nanoTime();
        LibraryInputReader reader = new LibraryInputReader(args);
        LibraryInput input;

        try {
            input = reader.read();
        } catch (MissedArgumentsException ignore) {
            return;
        }

        String projectPath = input.getProjectPath();
        String androidSdkPath = input.getAndroidSdkPath();
        String packageName = input.getPackageName();
        String mainActivity = input.getMainActivity();
        String deployPath = GreenCat.getDexDeployPath();
        File lambdaDir = GreenCat.getLambdaDir(projectPath);
        File dexDir = GreenCat.getDexBuildDir(projectPath);

        Telemetry resolveTelemetry = new Telemetry();
        resolveTelemetry.green("* * * * * * * * * * * * * * *");
        resolveTelemetry.green("* GREENCAT TELEMETRY REPORT *");
        resolveTelemetry.green("* * * * * * * * * * * * * * *");

        TaskExecutor.Result result = resolveProject(resolveTelemetry, projectPath);
        Map<Module, Set<File>> diff;

        try {
            GitDiffMessage message = (GitDiffMessage) result.message;
            diff = message.getFiles();
        } catch (ClassCastException e) {
            Log.e("Unexpected message type: %s", result.message);
            e.printStackTrace();
            return;
        }

        if (result.status == ExecutionStatus.TERMINATED) {
            resolveTelemetry.warn("TASK EXECUTION TERMINATED");
            resolveTelemetry.print();
            return;

        } else if (result.status == ExecutionStatus.ERROR) {
            resolveTelemetry.warn("TASK EXECUTION FAILED");
            resolveTelemetry.print();
            return;
        }

        resolveTelemetry.print();
        Telemetry buildTelemetry = new Telemetry();

        if (!doIncrementalBuild(input, diff)) {
            buildTelemetry.error("COMPILATION FAILED");
            return;
        }

        if (!prepareClassesForDex(input, diff)) {
            buildTelemetry.error("PREPARING CLASSES FOR DEX FAILED");
            return;
        }

        long endTime = System.nanoTime();
        buildTelemetry.message("DEPLOYMENT COMPLETE IN %s SEC", Utils.formatNanoTimeToSeconds(endTime - startTime));
        buildTelemetry.print();
    }

    private static TaskExecutor.Result resolveProject(Telemetry telemetry, String projectPath) {
        ProjectSetup launchMessage = new ProjectSetup(projectPath);
        TaskExecutor executor = TaskExecutor.create(launchMessage, telemetry)
                .add(new ProjectSetupTask())
                .add(new ModulesResolveTask())
                .add(new GitDiffTask());
        return executor.execute();
    }

    private static final String FIND_WITH_REMOVE_COMMAND = "find %s -type f ! \\( %s \\) -print0 | xargs -0 rm --";

    private static boolean prepareClassesForDex(LibraryInput input, Map<Module, Set<File>> diff) {
        Telemetry telemetry = new Telemetry();
        telemetry.message(" ");
        telemetry.message("Cleaning up unused .class files...");
        telemetry.print();

        StringBuilder filesKeepBuilder = new StringBuilder();
        boolean hasFile = false;

        for (Module module : diff.keySet()) {
            Set<File> files = diff.get(module);

            for (File file : files) {
                String path = file.getAbsolutePath();
                int startIndex = path.lastIndexOf("/") + 1;
                int endIndex = path.lastIndexOf(".");
                String name = path.substring(startIndex, endIndex);
                String fileRegExp = String.format("'%s*.class'", name);

                filesKeepBuilder.append(hasFile ? " -o " : "").append("-name ").append(fileRegExp);
                hasFile = true;
            }
        }

        String projectPath = input.getProjectPath();
        String lambdaPath = GreenCat.getLambdaDir(projectPath).getAbsolutePath();
        String cmd = String.format(FIND_WITH_REMOVE_COMMAND, lambdaPath, filesKeepBuilder.toString());
        List<String> output = CommandExecutor.execOnInputStream(cmd);

        for (String line : output) {
            telemetry.message(line);
        }

        telemetry.print();
        return output.size() == 0;
    }

    private static boolean doIncrementalBuild(LibraryInput input, Map<Module, Set<File>> diff) {
        boolean success = true;
        int count = diff.size();
        ExecutorService service = Executors.newFixedThreadPool(count);
        String projectPath = input.getProjectPath();
        String classpath = input.getClasspath();
        CountDownLatch latch = new CountDownLatch(count);
        List<Telemetry> failedReports = Collections.synchronizedList(new ArrayList<>());

        for (Module module : diff.keySet()) {
            service.submit(() -> {
                Telemetry buildReport = new Telemetry();
                buildReport.message("Starting incremental build for module [%s]", module.name);
                buildReport.print();

                IncrementalBuild build = new IncrementalBuild(projectPath, classpath, module, diff);
                IncrementalBuild.Result result = build.submit();

                if (result.status == ExecutionStatus.SUCCESS) {
                    buildReport.message("Building complete for module [%s]", module.name);

                } else if (result.status == ExecutionStatus.TERMINATED) {
                    buildReport.message("Building terminated for module [%s]", module.name);
                    failedReports.add(result.telemetry);

                } else {
                    buildReport.message("Building failed for module [%s]", module.name);
                    failedReports.add(result.telemetry);
                }

                buildReport.print();
                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (failedReports.size() > 0) {
            for (Telemetry telemetry : failedReports) {
                telemetry.print();
            }
            success = false;
        }

        service.shutdown();
        return success;
    }
}
