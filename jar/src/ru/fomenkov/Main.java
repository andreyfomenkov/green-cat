package ru.fomenkov;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.configuration.Configuration;
import ru.fomenkov.configuration.ConfigurationReader;
import ru.fomenkov.configuration.Property;
import ru.fomenkov.exception.ConfigurationParsingException;
import ru.fomenkov.exception.MissedArgumentsException;
import ru.fomenkov.input.LibraryInput;
import ru.fomenkov.input.LibraryInputReader;
import ru.fomenkov.message.GitDiffMessage;
import ru.fomenkov.message.ProjectSetup;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.TaskExecutor;
import ru.fomenkov.task.deploy.DeployTask;
import ru.fomenkov.task.dex.DexTask;
import ru.fomenkov.task.diff.GitDiffTask;
import ru.fomenkov.task.resolve.ModulesResolveTask;
import ru.fomenkov.task.restart.RestartAppTask;
import ru.fomenkov.task.setup.ProjectSetupTask;
import ru.fomenkov.telemetry.Telemetry;
import ru.fomenkov.util.Log;
import ru.fomenkov.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        Telemetry configTelemetry = new Telemetry();
        Configuration configLauncher = readLauncherConfiguration(configTelemetry);
        Configuration configLocal = setupLocalConfiguration(configTelemetry);

        if (configLauncher == null) {
            configTelemetry.warn("Reading launcher configuration failed");
            configTelemetry.print();
            return;
        }

        if (configLocal == null) {
            configTelemetry.warn("Setting up local configuration failed");
            configTelemetry.print();
            return;
        }

        long startTime = System.nanoTime();
        LibraryInputReader reader = new LibraryInputReader(args);
        LibraryInput input;

        try {
            input = reader.read();
        } catch (MissedArgumentsException ignore) {
            return;
        }

        String projectPath = input.getProjectPath();
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
            buildTelemetry.print();
            return;
        }

        if (!prepareClassesForDex(input, diff)) {
            buildTelemetry.error("PREPARING CLASSES FOR DEX FAILED");
            buildTelemetry.print();
            return;
        }

        String androidSdkPath = input.getAndroidSdkPath();
        File lambdaDir = GreenCat.getLambdaDir(projectPath);
        File dexDir = GreenCat.getDexBuildDir(projectPath);
        String packageName = configLauncher.get(Property.PACKAGE, "");
        String mainActivity = configLauncher.get(Property.LAUNCHER_ACTIVITY, "");
        String deployPath = GreenCat.getDexDeployPath();

        if (!makeDexAndDeploy(androidSdkPath, lambdaDir, dexDir, deployPath, packageName, mainActivity)) {
            buildTelemetry.error("DEXING AND DEPLOYMENT FAILED");
            buildTelemetry.print();
            return;
        }

        long endTime = System.nanoTime();
        buildTelemetry.message("DEPLOYMENT COMPLETE IN %s SEC", Utils.formatNanoTimeToSeconds(endTime - startTime));
        buildTelemetry.print();
    }

    private static Configuration readLauncherConfiguration(Telemetry telemetry) {
        ConfigurationReader reader = new ConfigurationReader(GreenCat.PROPERTIES_FILE);
        Configuration configuration;

        try {
            configuration = reader.read();
        } catch (IOException | ConfigurationParsingException e) {
            e.printStackTrace();
            telemetry.error("Error reading %s file: %s", GreenCat.PROPERTIES_FILE, e.getMessage());
            return null;
        }

        if (configuration.get(Property.PACKAGE, "").isEmpty()) {
            telemetry.error("Property %s is not set", Property.PACKAGE);
            return null;

        } else if (configuration.get(Property.LAUNCHER_ACTIVITY, "").isEmpty()) {
            telemetry.error("Property %s is not set", Property.LAUNCHER_ACTIVITY);
            return null;
        }
        return configuration;
    }

    private static Configuration setupLocalConfiguration(Telemetry telemetry) {
        Configuration configuration = null;
        return configuration;
    }

    private static boolean makeDexAndDeploy(String androidSdkPath, File lambdaDir, File dexDir, String deployPath,
                                            String appPackage, String launcherActivity) {
        Telemetry telemetry = new Telemetry();
        TaskExecutor.Result result = TaskExecutor.create(null, telemetry)
                .add(new DexTask(androidSdkPath, lambdaDir, dexDir))
                .add(new DeployTask(androidSdkPath, dexDir.getAbsolutePath(), deployPath))
                .add(new RestartAppTask(androidSdkPath, appPackage, launcherActivity))
                .execute();
        telemetry.print();
        return result.status == ExecutionStatus.SUCCESS;
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

    private static TaskExecutor.Result resolveProject(Telemetry telemetry, String projectPath) {
        ProjectSetup launchMessage = new ProjectSetup(projectPath);
        TaskExecutor executor = TaskExecutor.create(launchMessage, telemetry)
                .add(new ProjectSetupTask())
                .add(new ModulesResolveTask())
                .add(new GitDiffTask());
        return executor.execute();
    }
}
