package ru.fomenkov;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
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
        long startTime = System.nanoTime();
        LibraryInputReader reader = new LibraryInputReader(args);
        LibraryInput input;

        try {
            input = reader.read();
        } catch (MissedArgumentsException error) {
            Telemetry.err("Failed to read arguments: %s", error.getMessage());
            return;
        }
        Configuration config = readConfigFile();

        if (config == null) {
            Telemetry.err("Failed to read configuration file");
            return;
        }
        String projectPath = input.getProjectPath();
        Telemetry.log("===================================================");
        Telemetry.log("GreenCat v" + GreenCat.VERSION);
        Telemetry.log("GitHub: https://github.com/andreyfomenkov/green-cat");
        Telemetry.log("===================================================");

        TaskExecutor.Result result = resolveProject(projectPath);
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
            Telemetry.err("TASK EXECUTION TERMINATED\n");
            return;

        } else if (result.status == ExecutionStatus.ERROR) {
            Telemetry.err("TASK EXECUTION FAILED\n");
            return;
        }
        printJdkInfo();

        if (!doIncrementalBuild(input, diff)) {
            Telemetry.err("COMPILATION FAILED\n");
            return;
        }
        if (!prepareClassesForDex(input, diff)) {
            Telemetry.err("PREPARING CLASSES FOR DEX FAILED\n");
            return;
        }
        String androidSdkPath = input.getAndroidSdkPath();
        File dexDir = GreenCat.getDexBuildDir(projectPath);
        String packageName = config.get(Property.PACKAGE);
        String mainActivity = config.get(Property.LAUNCHER_ACTIVITY);
        String deployPath = GreenCat.getDexDeployPath();

        if (!makeDexAndDeploy(androidSdkPath, dexDir, deployPath, packageName, mainActivity)) {
            Telemetry.err("DEXING AND DEPLOYMENT FAILED\n");
            return;
        }

        long endTime = System.nanoTime();
        Telemetry.log("DEPLOYMENT COMPLETE IN %s SEC\n", Utils.formatNanoTimeToSeconds(endTime - startTime));
    }

    private static void printJdkInfo() {
        String cmd = CommandLineBuilder.create("java -version").build();
        List<String> output = CommandExecutor.execOnErrorStream(cmd);
        Telemetry.log("java -version");

        for (String line : output) {
            Telemetry.log(line);
        }
        Telemetry.log(" ");
        Telemetry.log("javac -version");
        cmd = CommandLineBuilder.create("javac -version").build();
        output = CommandExecutor.execOnInputStream(cmd);

        for (String line : output) {
            Telemetry.log(line);
        }
    }

    private static Configuration readConfigFile() {
        ConfigurationReader reader = new ConfigurationReader(GreenCat.CONFIG_FILE);
        Configuration configuration;

        try {
            configuration = reader.read();
        } catch (IOException | ConfigurationParsingException e) {
            e.printStackTrace();
            Telemetry.err("Error reading %s file: %s", GreenCat.CONFIG_FILE, e.getMessage());
            return null;
        }

        if (configuration.get(Property.PACKAGE).isEmpty()) {
            Telemetry.err("Property %s is not set", Property.PACKAGE);
            return null;

        } else if (configuration.get(Property.LAUNCHER_ACTIVITY).isEmpty()) {
            Telemetry.err("Property %s is not set", Property.LAUNCHER_ACTIVITY);
            return null;
        }
        return configuration;
    }

    private static boolean makeDexAndDeploy(String androidSdkPath, File dexDir, String deployPath,
                                            String appPackage, String launcherActivity) {
        TaskExecutor.Result result = TaskExecutor.create(null)
                .add(new DexTask(androidSdkPath, dexDir))
                .add(new DeployTask(androidSdkPath, dexDir.getAbsolutePath(), deployPath))
                .add(new RestartAppTask(androidSdkPath, appPackage, launcherActivity))
                .execute();
        return result.status == ExecutionStatus.SUCCESS;
    }

    private static final String FIND_WITH_REMOVE_COMMAND = "find %s -type f ! \\( %s \\) -print0 | xargs -0 rm --";

    private static boolean prepareClassesForDex(LibraryInput input, Map<Module, Set<File>> diff) {
        Telemetry.log(" ");
        Telemetry.log("Cleaning up unused .class files...\n");

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
        String cmd = String.format(FIND_WITH_REMOVE_COMMAND, "", filesKeepBuilder); // TODO: 1st argument???
        List<String> output = CommandExecutor.execOnInputStream(cmd);

        for (String line : output) {
            Telemetry.log(line);
        }
        Telemetry.log(" ");
        return output.size() == 0;
    }

    private static boolean doIncrementalBuild(LibraryInput input, Map<Module, Set<File>> diff) {
        boolean success = true;
        int count = diff.size();
        ExecutorService service = Executors.newFixedThreadPool(count);
        String projectPath = input.getProjectPath();
        String classpath = input.getClasspath();
        CountDownLatch latch = new CountDownLatch(count);
        List<String> failedReports = Collections.synchronizedList(new ArrayList<>());

        for (Module module : diff.keySet()) {
            service.submit(() -> {
                Telemetry.log("Starting incremental build for module [%s]\n", module.name);
                IncrementalBuild build = new IncrementalBuild(projectPath, classpath, module, diff);
                IncrementalBuild.Result result = build.submit();

                if (result.status == ExecutionStatus.SUCCESS) {
                    Telemetry.log("Building complete for module [%s]\n", module.name);

                } else if (result.status == ExecutionStatus.TERMINATED) {
                    Telemetry.err("Building terminated for module [%s]", module.name);
//                    failedReports.add(result.report); TODO: report?

                } else {
                    Telemetry.err("Building failed for module [%s]", module.name);
//                    failedReports.add(result.report); TODO: report?
                }
                Telemetry.log(" ");
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (failedReports.size() > 0) {
            for (String report : failedReports) {
                Telemetry.err(report);
            }
            success = false;
        }
        service.shutdown();
        return success;
    }

    private static TaskExecutor.Result resolveProject(String projectPath) {
        ProjectSetup launchMessage = new ProjectSetup(projectPath);
        TaskExecutor executor = TaskExecutor.create(launchMessage)
                .add(new ProjectSetupTask())
                .add(new ModulesResolveTask())
                .add(new GitDiffTask());
        return executor.execute();
    }
}
