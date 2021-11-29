package ru.fomenkov.task.dex;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.ClassDesugarMessage;
import ru.fomenkov.message.DexMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

public class DexTask implements Task<ClassDesugarMessage, DexMessage> {

    private final String androidSdkPath;
    private final File dexDir;

    public DexTask(String androidSdkPath, File dexDir) {
        this.androidSdkPath = androidSdkPath;
        this.dexDir = dexDir;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.MAKE_DEX;
    }

    @Override
    public DexMessage exec(ClassDesugarMessage message) {
        Telemetry.log("Looking for Android SDK directory...");

        if (androidSdkPath != null) {
            Telemetry.log("Android SDK modulePath: %s", androidSdkPath);
        } else {
            Telemetry.err("Failed to get Android SDK modulePath");
            return new DexMessage(ExecutionStatus.ERROR, "Failed to get Android SDK modulePath");
        }
        File buildToolsDir = new File(androidSdkPath + "/build-tools");

        if (buildToolsDir.exists()) {
            File[] contents = buildToolsDir.listFiles();

            if (contents == null || contents.length == 0) {
                Telemetry.err("Can't list build tools directories");
                return new DexMessage(ExecutionStatus.ERROR, "Can't list build tools directories");
            }
            List<File> dirList = new ArrayList<>();

            for (File file : contents) {
                if (file.isDirectory()) {
                    dirList.add(file);
                    Telemetry.log("Found build tools directory: %s", file.getName());
                }
            }
            dirList.sort(Comparator.naturalOrder());

            if (dirList.isEmpty()) {
                Telemetry.err("No build tools installed for Android SDK");
                return new DexMessage(ExecutionStatus.ERROR, "No build tools installed for Android SDK");
            }
            int size = dirList.size();
            String buildToolsPath = dirList.get(size - 1).getAbsolutePath();
            Telemetry.log("Using build tools %s for dx tool", dirList.get(size - 1).getName());

            if (!dexDir.exists() && !dexDir.mkdirs()) {
                Telemetry.err("Failed to create modulePath: %s", dexDir.getAbsolutePath());
                return new DexMessage(ExecutionStatus.ERROR, "Failed to create DEX file directory");
            }
            Telemetry.log("DEX files directory: %s", dexDir.getAbsolutePath());

            // TODO: fix compile path

            if (makeDex(buildToolsPath, "/Users/andrey.fomenkov/Workspace/ok/build/greencat/compile", dexDir.getAbsolutePath())) {
                Telemetry.log("DEX file(s) created");
                return new DexMessage();
            } else {
                Telemetry.err("Failed to create DEX file(ss)");
                return new DexMessage(ExecutionStatus.ERROR, "Failed to create DEX file");
            }
        } else {
            Telemetry.err("Directory /build-tools doesn't exist in Android SDK modulePath");
            return new DexMessage(ExecutionStatus.ERROR, "Directory /build-tools doesn't exist in Android SDK modulePath");
        }
    }

    private boolean makeDex(String buildToolsPath, String compilePath, String dexOutputPath) {
        String cmd = CommandLineBuilder.create(buildToolsPath + "/d8")
                .add(new Parameter(compilePath + "/**/*.class"))
                .add(new Parameter("--output " + dexOutputPath))
                .build();
        List<String> output = CommandExecutor.execOnErrorStream(cmd);

        for (String line : output) {
            Telemetry.log(line);
        }
        return true;
    }
}
