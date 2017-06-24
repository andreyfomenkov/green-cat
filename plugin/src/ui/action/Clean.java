package ui.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import core.GreenCat;
import core.message.ProjectSetup;
import core.task.TaskExecutor;
import core.task.cleanup.CleanupTask;
import core.task.diff.GitDiff;
import core.task.javac.CompileWithJavac;

import java.io.File;

public class Clean extends ProjectAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        cleanBuildDirectory(project);
    }

    private void cleanBuildDirectory(Project project) {
        String projectPath = project.getBasePath();
        File objPath = GreenCat.getObjPath(projectPath);
        ProjectSetup message = new ProjectSetup(projectPath);

        TaskExecutor.Result result = TaskExecutor.create(message, getTelemetryLogger())
                .add(new CleanupTask())
                .add(new GitDiff())
                .add(new CompileWithJavac(project, objPath))
                .execute();

        displayTaskExecutionResult(project, result);
    }
}
