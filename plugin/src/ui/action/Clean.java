package ui.action;

import async.AsyncExecutor;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import command.CommandExecutor;
import command.CommandLineBuilder;
import command.Parameter;
import result.CleanResult;
import ui.GreenCat;
import ui.util.EventLog;
import ui.util.Utils;
import ui.window.TelemetryToolWindow;

import java.util.List;

public class Clean extends ProjectAction {

    private boolean firstStart = true;

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        TelemetryToolWindow window = TelemetryToolWindow.get(project);
        String androidSdkPath = Utils.getAndroidSdkPath(project);
        String adbPath = androidSdkPath + "/platform-tools/adb";

        if (firstStart) {
            firstStart = false;
            window.show();
        }

        window.clear();
        clean(window, adbPath);
    }

    private static class DetailedCleanReport {

        public final CleanResult result;
        public final ImmutableList<String> output;

        public DetailedCleanReport(CleanResult result, List<String> output) {
            this.result = result;
            this.output = ImmutableList.copyOf(output);
        }
    }

    private void clean(TelemetryToolWindow window, String adbPath) {
        setActionEnabled(false);
        new AsyncExecutor<DetailedCleanReport>() {

            @Override
            public DetailedCleanReport onBackground() {
                String cmd = CommandLineBuilder.create(adbPath + " shell")
                        .add(new Parameter("rm -rf", GreenCat.SDCARD_DEPLOY_PATH))
                        .build();

                List<String> output = CommandExecutor.execOnErrorStream(window, cmd);
                CleanResult result;

                if (output.size() == 0) {
                    result = CleanResult.OK;
                } else {
                    String line = output.get(0);

                    if (line.contains("no devices")) {
                        result = CleanResult.NO_DEVICES_CONNECTED;
                    } else if (line.contains("more than one device")) {
                        result = CleanResult.MULTIPLE_DEVICES_CONNECTED;
                    } else {
                        result = CleanResult.UNKNOWN_ERROR;
                    }
                }

                return new DetailedCleanReport(result, output);
            }

            @Override
            public void onActionComplete(DetailedCleanReport report) {
                if (report.result == CleanResult.OK) {
                    window.green(report.result.message);
                    EventLog.info(report.result.message);
                } else {
                    window.error(report.result.message);
                    EventLog.error(report.result.message);
                }

                window.update();
                setActionEnabled(true);
            }
        }.start();
    }
}