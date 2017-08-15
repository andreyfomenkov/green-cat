package ui.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import ui.window.TelemetryToolWindow;

import java.io.IOException;

public class Deploy extends ProjectAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        TelemetryToolWindow window = TelemetryToolWindow.get(project);

        window.message("Running script from jar...");
        try {
            Runtime.getRuntime().exec("java -jar /home/afomenkov/workspace/shell-script-run/out/artifacts/shell_script_run_jar/shell-script-run.jar");
        } catch (IOException e) {
            window.error("Error %s", e.getMessage());
        }
        window.green("Running complete");
        window.show();
    }
}
