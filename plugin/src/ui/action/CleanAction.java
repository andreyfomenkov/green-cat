package ui.action;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;

public class CleanAction extends AbstractProjectAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        String id = "CMVC Console";


        TextConsoleBuilderFactory factory = TextConsoleBuilderFactory.getInstance();
        TextConsoleBuilder builder = factory.createBuilder(project);
        ConsoleView view = builder.getConsole();

        view.print("ABC", ConsoleViewContentType.NORMAL_OUTPUT);

        //OSProcessHandler handler = new OSProcessHandler(proc, command);
        //view.attachToProcess(handler);

        ToolWindow window = manager.getToolWindow(id);

        if (window == null) {
            window = manager.registerToolWindow(id, view.getComponent(), ToolWindowAnchor.BOTTOM);
            window.show(() -> System.out.println("Do something here"));
        }
    }
}
