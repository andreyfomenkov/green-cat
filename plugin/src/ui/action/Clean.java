package ui.action;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;

public class Clean extends ProjectAction {

    private ToolWindow window;
    private ConsoleView consoleView;

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
    }
}
