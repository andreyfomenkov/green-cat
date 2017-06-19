package ui.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import core.message.ProjectSetup;
import core.task.TaskExecutor;
import core.task.cleanup.CleanupTask;
import core.task.diff.GitDiff;

public class Clean extends ProjectAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        cleanBuildDirectory(project);
    }

    private void cleanBuildDirectory(Project project) {
        String projectDir = project.getBasePath();
        ProjectSetup message = new ProjectSetup("Setting up project base path");

        TaskExecutor.Result result = TaskExecutor.create(message, getTelemetryLogger())
                .add(new CleanupTask(projectDir))
                .add(new GitDiff(projectDir))
                .execute();

        displayTaskExecutionResult(project, result);
    }
}
