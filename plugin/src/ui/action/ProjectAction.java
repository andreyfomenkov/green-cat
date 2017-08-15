package ui.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.TaskExecutor;
import ru.fomenkov.telemetry.Telemetry;
import core.util.Utils;
import ui.util.EventLog;
import ui.window.TelemetryToolWindow;

import static ui.util.Utils.isNullOrEmpty;

public abstract class ProjectAction extends AnAction {

    private final Telemetry telemetry = new Telemetry();

    protected Project getProject(AnActionEvent event) {
        return event.getData(PlatformDataKeys.PROJECT);
    }

    protected Telemetry getTelemetryLogger() {
        return telemetry;
    }

    protected void displayTaskExecutionResult(Project project, TaskExecutor.Result result) {
        TelemetryToolWindow window = TelemetryToolWindow.get(project);
        window.clear();
        telemetry.populateToolWindow(window);

        if (result.status == ExecutionStatus.SUCCESS) {
            String message = String.format("Task(s) execution complete in %s sec", Utils.formatNanoTimeToSeconds(result.nanoTime));
            EventLog.info(message);
        } else if (result.status == ExecutionStatus.TERMINATED) {
            EventLog.warn("Task(s) execution terminated");
        } else {
            String taskName = result.task.getClass().getSimpleName();
            String message;

            if (isNullOrEmpty(result.description)) {
                message = String.format("Failed to execute task %s", taskName);
            } else {
                message = String.format("Failed to execute task %s: %s", taskName, result.description);
            }

            EventLog.error(message);
            window.show();
        }
    }
}
