package ui.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

public abstract class AbstractProjectAction extends AnAction {

    protected Project getProject(AnActionEvent event) {
        return event.getData(PlatformDataKeys.PROJECT);
    }
}
