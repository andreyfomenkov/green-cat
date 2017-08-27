package ui.action;

import async.AsyncExecutor;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.macro.ClasspathMacro;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import command.CommandExecutor;
import command.CommandLineBuilder;
import command.Parameter;
import result.DeployResult;
import ui.GreenCat;
import ui.util.EventLog;
import ui.util.Utils;
import ui.window.TelemetryToolWindow;

import javax.annotation.Nullable;
import java.util.List;

public class Deploy extends ProjectAction {

    private boolean firstStart = true;

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = getProject(event);
        DataContext context = event.getDataContext();
        TelemetryToolWindow window = TelemetryToolWindow.get(project);

        if (firstStart) {
            firstStart = false;
            window.show();
        }

        window.clear();
        deploy(window, project, context);
    }

    private static class DetailedDeployReport {

        public final DeployResult result;
        public final ImmutableList<String> output;

        public DetailedDeployReport(DeployResult result, List<String> output) {
            this.result = result;
            this.output = ImmutableList.copyOf(output);
        }
    }

    private String composeModulesArgument(Project project) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        StringBuilder builder = new StringBuilder();

        for(int i = 0; i < modules.length; i++) {
            Module module = modules[i];
            String path = module.getModuleFilePath();
            builder.append(i == 0 ? "" : ":").append(path);
        }

        return builder.toString();
    }

    @Nullable
    private String composeClasspathArgument(DataContext context) {
        ClasspathMacro macro = new ClasspathMacro();
        String value = macro.expand(context);

        if (value != null) {
            value = value.trim().replace(" ", "%20");
        }

        return value;
    }

    private void deploy(TelemetryToolWindow window, Project project, DataContext context) {
        setActionEnabled(false);
        String projectPath = project.getBasePath();
        String modules = composeModulesArgument(project);
        String classpath = composeClasspathArgument(context);
        String androidSdkPath = Utils.getAndroidSdkPath(project);
        String appPackage = "com.agoda.mobile.consumer.debug"; // TODO: read from launch.properties
        String launcherActivity = "com.agoda.mobile.consumer.screens.splash.AppLauncherActivity"; // TODO: read from launch.properties

        window.message("Deployment in progress...");
        window.message(" ");
        window.update();

        new AsyncExecutor<DetailedDeployReport>() {

            @Override
            public DetailedDeployReport onBackground() {
                DeployResult result = executeJar(window, projectPath, modules, classpath, androidSdkPath, appPackage, launcherActivity);
                return new DetailedDeployReport(result, ImmutableList.of());
            }

            @Override
            public void onActionComplete(DetailedDeployReport report) {
                if (report.result == DeployResult.OK) {
                    window.green(report.result.message);
                    EventLog.info(report.result.message);

                } else if (report.result == DeployResult.TERMINATED) {
                    window.warn(report.result.message);
                    EventLog.warn(report.result.message);

                } else {
                    window.error(report.result.message);
                    EventLog.error(report.result.message);
                }

                window.update();
                setActionEnabled(true);
            }
        }.start();
    }

    private DeployResult executeJar(TelemetryToolWindow window, String projectPath, String modules, String classpath,
                            String androidSdkPath, String appPackage, String launcherActivity) {
        String jarPath = projectPath + GreenCat.GREENCAT_JAR_PATH;
        String cmd = CommandLineBuilder.create("java -jar")
                .add(new Parameter(jarPath))
                .add(new Parameter("-d", projectPath))
                .add(new Parameter("-m", modules))
                .add(new Parameter("-cp", classpath))
                .add(new Parameter("-sdk", androidSdkPath))
                .add(new Parameter("-p", appPackage))
                .add(new Parameter("-a", launcherActivity))
                .build();

        List<String> output = CommandExecutor.execOnInputStream(window, cmd);
        String line = output.get(output.size() - 1).toLowerCase();

        if (line.contains("terminated")) {
            EventLog.warn("Deploying terminated");
            return DeployResult.TERMINATED;

        } else if (line.contains("failed")) {
            EventLog.error("Deploying failed");
            return DeployResult.FAILED;

        } else {
            EventLog.error("Deploying OK");
            return DeployResult.OK;
        }
    }
}
