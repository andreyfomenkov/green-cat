package ui.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import ui.window.TelemetryToolWindow;

public abstract class ProjectAction extends AnAction {

    protected Project getProject(AnActionEvent event) {
        return event.getData(PlatformDataKeys.PROJECT);
    }

    protected TelemetryToolWindow getToolWindow(Project project) {
        return TelemetryToolWindow.get(project);
    }
}
