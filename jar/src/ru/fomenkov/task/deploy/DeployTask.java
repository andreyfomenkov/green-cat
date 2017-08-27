package ru.fomenkov.task.deploy;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.DeployMessage;
import ru.fomenkov.message.DexMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

import java.util.List;

import static ru.fomenkov.util.Utils.fileExists;

public class DeployTask implements Task<DexMessage, DeployMessage> {

    private final String androidSdkPath;
    private final String dexPath;
    private final String deployPath;

    public DeployTask(String androidSdkPath, String dexPath, String deployPath) {
        this.androidSdkPath = androidSdkPath;
        this.dexPath = dexPath;
        this.deployPath = deployPath;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.DEPLOY_ON_DEVICE;
    }

    @Override
    public DeployMessage exec(Telemetry telemetry, DexMessage message) {
        telemetry.message("Pushing DEX file to device: %s", deployPath);

        if (fileExists(dexPath)) {
            telemetry.message("DEX modulePath: %s", dexPath);
        } else {
            telemetry.error("DEX modulePath not found: %s", dexPath);
            return new DeployMessage(ExecutionStatus.ERROR, "DEX file not found");
        }

        String cmd = CommandLineBuilder.create(androidSdkPath + "/platform-tools/adb")
                .add(new Parameter("push", dexPath + "/classes.dex"))// TODO
                .add(new Parameter(deployPath + "/delta.dex")) // TODO
                .build();

        List<String> output = CommandExecutor.execOnInputStream(cmd);
        boolean success = false;

        for (String line : output) {
            if (line.contains("100%")) {
                success = true;
                continue;
            }

            if (!line.trim().startsWith("[")) {
                telemetry.message(line.replace("%", ""));
            }
        }

        if (success) {
            return new DeployMessage(ExecutionStatus.SUCCESS, "Deployment complete");
        } else {
            return new DeployMessage(ExecutionStatus.ERROR, "Failed to deploy changes on Android device");
        }
    }
}
