package core.task.restart;

import core.command.CommandExecutor;
import core.message.DeployMessage;
import core.message.RestartAppMessage;
import core.task.Task;
import core.task.TaskPurpose;
import core.telemetry.Telemetry;

public class RestartAppTask implements Task<DeployMessage, RestartAppMessage> {

    private final String androidSdkPath;
    private final String appPackage;
    private final String mainActivity;

    public RestartAppTask(String androidSdkPath, String appPackage, String mainActivity) {
        this.androidSdkPath = androidSdkPath;
        this.appPackage = appPackage;
        this.mainActivity = mainActivity;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.RESTART_APPLICATION;
    }

    @Override
    public RestartAppMessage exec(Telemetry telemetry, DeployMessage message) {
        telemetry.message("Application package: %s", appPackage);
        telemetry.message("Application launcher activity: %s", mainActivity);
        telemetry.message("Restarting...");

        String cmdForceStop = String.format("adb shell am force-stop %s", appPackage);
        String cmdStart = String.format("adb shell am start -n \"%s/%s\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER", appPackage, mainActivity);
        CommandExecutor.exec(cmdForceStop);
        CommandExecutor.exec(cmdStart);
        return null;
    }
}
