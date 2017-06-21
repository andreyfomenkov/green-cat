package ui.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import core.message.ProjectSetup;
import core.task.TaskExecutor;
import core.task.cleanup.CleanupTask;
import core.task.diff.GitDiff;
import core.task.javac.CompileWithJavac;

public class Clean extends ProjectAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        cleanBuildDirectory(project);
    }

    private void cleanBuildDirectory(Project project) {
        String projectPath = project.getBasePath();
        ProjectSetup message = new ProjectSetup(projectPath);

        TaskExecutor.Result result = TaskExecutor.create(message, getTelemetryLogger())
                .add(new CleanupTask())
                .add(new GitDiff())
                .add(new CompileWithJavac(project))
                .execute();

        displayTaskExecutionResult(project, result);
    }
}
