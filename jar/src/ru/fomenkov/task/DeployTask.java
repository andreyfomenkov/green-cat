package ru.fomenkov.task;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.DeployMessage;
import ru.fomenkov.message.DexMessage;
import ru.fomenkov.telemetry.Telemetry;

import java.util.List;

import static ru.fomenkov.util.Utils.fileExists;

public class DeployTask implements Task<DexMessage, DeployMessage> {

    private final String androidSdkPath;
    private final String deployFilePath;

    public DeployTask(String androidSdkPath, String deployPath, String filename) {
        this.androidSdkPath = androidSdkPath;
        this.deployFilePath = deployPath + "/" + filename;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.DEPLOY_ON_DEVICE;
    }

    @Override
    public DeployMessage exec(Telemetry telemetry, DexMessage message) {
        telemetry.message("Pushing DEX file to device: %s", deployFilePath);

        if (fileExists(message.dexFilePath)) {
            telemetry.message("DEX file: %s", message.dexFilePath);
        } else {
            telemetry.error("DEX file not found: %s", message.dexFilePath);
            return new DeployMessage(ExecutionStatus.ERROR, "DEX file not found");
        }

        String cmd = CommandLineBuilder.create(androidSdkPath + "/platform-tools/adb")
                .add(new Parameter("push", message.dexFilePath))
                .add(new Parameter(deployFilePath))
                .build();
        List<String> output = CommandExecutor.exec(cmd);
        boolean success = false;

        for (String line : output) {
            if (line.contains("100%")) {
                success = true;
                continue;
            }
            telemetry.message(line.replace("%", ""));
        }

        if (success) {
            return new DeployMessage(ExecutionStatus.SUCCESS, "Deployment complete");
        } else {
            return new DeployMessage(ExecutionStatus.ERROR, "Failed to deploy changes on Android device");
        }
    }
}
