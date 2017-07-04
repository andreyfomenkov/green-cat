package ui.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import core.GreenCat;
import core.message.ProjectSetup;
import core.task.DeployTask;
import core.task.TaskExecutor;
import core.task.cleanup.CleanupTask;
import core.task.dex.DexTask;
import core.task.diff.GitDiff;
import core.task.javac.CompileWithJavac;
import core.task.restart.RestartAppTask;
import core.task.retrolambda.RetrolambdaTask;
import core.util.Utils;

import java.io.File;

public class Clean extends ProjectAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        DataContext context = event.getDataContext();
        cleanBuildDirectory(project, context);
    }

    private void cleanBuildDirectory(Project project, DataContext context) {
        String projectPath = project.getBasePath();
        String androidSdkPath = Utils.getAndroidSdkPath(project);
        String deployPath = GreenCat.getDexDeployPath();
        File objDir = GreenCat.getObjDir(projectPath);
        File dexDir = GreenCat.getDexBuildDir(projectPath);
        ProjectSetup message = new ProjectSetup(projectPath);

        TaskExecutor.Result result = TaskExecutor.create(message, getTelemetryLogger())
                .add(new CleanupTask())
                .add(new GitDiff())
                .add(new CompileWithJavac(context, objDir))
                .add(new RetrolambdaTask(objDir.getAbsolutePath()))
                .add(new DexTask(androidSdkPath, objDir, dexDir))
                .add(new DeployTask(androidSdkPath, deployPath, "delta.dex"))
                .add(new RestartAppTask(androidSdkPath, "<place_package_here>", "<place_main_activity_here>"))
                .execute();

        displayTaskExecutionResult(project, result);
    }
}
