package ui.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import ui.window.TelemetryToolWindow;

public class Deploy extends ProjectAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        TelemetryToolWindow window = TelemetryToolWindow.get(project);

        window.message("Regular message: %d", System.currentTimeMillis());
        window.warn("Warning message: %d", System.currentTimeMillis());
        window.error("Error message: %d", System.currentTimeMillis());
        window.green("Green message: %d", System.currentTimeMillis());
        window.message(" ");
    }
}
