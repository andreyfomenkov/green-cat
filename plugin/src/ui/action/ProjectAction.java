package ui.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

public abstract class ProjectAction extends AnAction {

    private boolean enabled = true;

    protected Project getProject(AnActionEvent event) {
        return event.getData(PlatformDataKeys.PROJECT);
    }

    protected void setActionEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setEnabled(enabled);
    }
}
