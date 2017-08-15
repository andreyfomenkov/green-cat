package ru.fomenkov.task.restart;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.message.DeployMessage;
import ru.fomenkov.message.RestartAppMessage;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

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

        String adbFilePath = androidSdkPath + "/platform-tools/adb";
        String action = "android.intent.action.MAIN";
        String category = "android.intent.category.LAUNCHER";
        String cmdForceStop = String.format("%s shell am force-stop %s", adbFilePath, appPackage);
        String cmdStart = String.format("%s shell am start -n \"%s/%s\" -a %s -c %s", adbFilePath, appPackage, mainActivity, action, category);
        CommandExecutor.exec(cmdForceStop);
        CommandExecutor.exec(cmdStart);
        return new RestartAppMessage();
    }
}
