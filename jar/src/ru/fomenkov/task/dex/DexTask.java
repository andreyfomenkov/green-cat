package ru.fomenkov.task.dex;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.ClassDesugarMessage;
import ru.fomenkov.message.DexMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DexTask implements Task<ClassDesugarMessage, DexMessage> {

    private final String androidSdkPath;
    private final File lambdaDir;
    private final File dexDir;

    public DexTask(String androidSdkPath, File lambdaDir, File dexDir) {
        this.androidSdkPath = androidSdkPath;
        this.lambdaDir = lambdaDir;
        this.dexDir = dexDir;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.MAKE_DEX;
    }

    @Override
    public DexMessage exec(Telemetry telemetry, ClassDesugarMessage message) {
        telemetry.message("Looking for Android SDK directory...");

        if (androidSdkPath != null) {
            telemetry.message("Android SDK path: %s", androidSdkPath);
        } else {
            telemetry.error("Failed to get Android SDK path");
            return new DexMessage(ExecutionStatus.ERROR, "Failed to get Android SDK path");
        }

        File buildToolsDir = new File(androidSdkPath + "/build-tools");

        if (buildToolsDir.exists()) {
            File[] contents = buildToolsDir.listFiles();

            if (contents == null || contents.length == 0) {
                telemetry.error("Can't list build tools directories");
                return new DexMessage(ExecutionStatus.ERROR, "Can't list build tools directories");
            }

            List<File> dirList = new ArrayList<>();

            for (File file : contents) {
                if (file.isDirectory()) {
                    dirList.add(file);
                    telemetry.message("Found build tools directory: %s", file.getName());
                }
            }

            dirList.sort(Comparator.naturalOrder());

            if (dirList.isEmpty()) {
                telemetry.error("No build tools installed for Android SDK");
                return new DexMessage(ExecutionStatus.ERROR, "No build tools installed for Android SDK");
            }

            int size = dirList.size();
            String dxToolPath = dirList.get(size - 1).getAbsolutePath();
            telemetry.message("Using build tools %s for dx tool", dirList.get(size - 1).getName());

            if (!dexDir.exists() && !dexDir.mkdirs()) {
                telemetry.error("Failed to create path: %s", dexDir.getAbsolutePath());
                return new DexMessage(ExecutionStatus.ERROR, "Failed to create DEX file directory");
            }

            telemetry.message("DEX files directory: %s", dexDir.getAbsolutePath());

            if (makeDex(telemetry, dxToolPath, lambdaDir.getAbsolutePath(), dexDir.getAbsolutePath())) {
                telemetry.message("DEX file(s) created");
                return new DexMessage();
            } else {
                telemetry.error("Failed to create DEX file(ss)");
                return new DexMessage(ExecutionStatus.ERROR, "Failed to create DEX file");
            }
        } else {
            telemetry.error("Directory /build-tools doesn't exist in Android SDK path");
            return new DexMessage(ExecutionStatus.ERROR, "Directory /build-tools doesn't exist in Android SDK path");
        }
    }

    private boolean makeDex(Telemetry telemetry, String dxToolPath, String lambdaPath, String dexPath) {
        String cmd = CommandLineBuilder.create(dxToolPath + "/dx")
                .add(new Parameter("--dex"))
                .add(new Parameter("--multi-dex"))
                .add(new Parameter("--output=" + dexPath))
                .add(new Parameter(lambdaPath + "/"))
                .build();

        List<String> output = CommandExecutor.exec(cmd, true);
        for (String line : output) {
            telemetry.message(line);
        }

        return true;
    }
}
