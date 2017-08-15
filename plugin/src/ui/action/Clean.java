package ui.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AppUIUtil;
import core.GreenCat;
import core.message.ProjectSetup;
import core.task.TaskExecutor;
import core.task.cleanup.CleanupTask;
import core.task.dex.DexTask;
import core.task.diff.GitDiff;
import core.task.javac.CompileWithJavac;
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
        File compileDir = GreenCat.getCompileDir(projectPath);
        File lambdaDir = GreenCat.getLambdaDir(projectPath);
        File dexDir = GreenCat.getDexBuildDir(projectPath);
        ProjectSetup message = new ProjectSetup(projectPath);

        TaskExecutor executor = TaskExecutor.create(message, getTelemetryLogger())
                .add(new CleanupTask())
                .add(new GitDiff())
                .add(new CompileWithJavac(context, projectPath, compileDir))
                .add(new RetrolambdaTask(compileDir.getAbsolutePath(), lambdaDir.getAbsolutePath()))
                .add(new DexTask(androidSdkPath, lambdaDir, dexDir));
                //.add(new DeployTask(androidSdkPath, deployPath, "delta.dex"))
                //.add(new RestartAppTask(androidSdkPath, "com.agoda.mobile.consumer.debug", "com.agoda.mobile.consumer.screens.splash.AppLauncherActivity"));

        TaskExecutor.Result result = executor.execute();
        displayTaskExecutionResult(project, result);

//        ApplicationManager.getApplication().executeOnPooledThread(() ->
//                ApplicationManager.getApplication().runReadAction(() -> {
//            TaskExecutor.Result result = executor.execute();
//            AppUIUtil.invokeOnEdt(() -> displayTaskExecutionResult(project, result));
//        }));
    }
}
