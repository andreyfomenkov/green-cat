package core.task;

import com.intellij.openapi.util.io.FileUtil;
import core.command.CommandExecutor;
import core.command.CommandLineBuilder;
import core.command.Parameter;
import core.message.DeployMessage;
import core.message.DexMessage;
import core.telemetry.Telemetry;

import java.util.List;

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

        if (FileUtil.exists(message.dexFilePath)) {
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
