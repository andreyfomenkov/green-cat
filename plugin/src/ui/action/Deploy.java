package ui.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import ui.window.TelemetryToolWindow;

public class Deploy extends ProjectAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        TelemetryToolWindow window = TelemetryToolWindow.get(project);
    }
}
