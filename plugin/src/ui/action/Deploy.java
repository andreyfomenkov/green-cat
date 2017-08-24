package ui.action;

import async.AsyncExecutor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import result.DeployResult;
import ui.util.EventLog;
import ui.util.Utils;
import ui.window.TelemetryToolWindow;

public class Deploy extends ProjectAction {

    private boolean firstStart = true;

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        String androidSdkPath = Utils.getAndroidSdkPath(project);
        String projectPath = project.getBasePath();

        TelemetryToolWindow window = TelemetryToolWindow.get(project);

        if (firstStart) {
            firstStart = false;
            window.show();
        }

        window.clear();
        deploy(window);
    }

    private void deploy(TelemetryToolWindow window) {
        setActionEnabled(false);
        window.message("Starting async task...");

        new AsyncExecutor<DeployResult>() {

            @Override
            public DeployResult onBackground() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return DeployResult.OK;
            }

            @Override
            public void onActionComplete(DeployResult result) {
                window.message("Async task complete: %s", result);
                EventLog.info("Async task complete");
                setActionEnabled(true);
            }
        }.start();
    }
}
