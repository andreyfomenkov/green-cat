package core.task.cleanup;

import com.sun.istack.internal.NotNull;
import core.GreenCat;
import core.message.CleanBuildMessage;
import core.message.ProjectSetup;
import core.task.ExecutionStatus;
import core.task.Task;
import core.task.TaskPurpose;
import core.telemetry.Telemetry;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class CleanupTask implements Task<ProjectSetup, CleanBuildMessage> {

    @Override
    @NotNull
    public TaskPurpose getPurpose() {
        return TaskPurpose.CLEANUP_BUILD;
    }

    @Override
    public CleanBuildMessage exec(Telemetry telemetry, ProjectSetup message) {
        String projectPath = message.getProjectPath();
        File projectDir = new File(projectPath);
        File buildDir = GreenCat.getBuildPath(projectPath);
        String description = null;

        if (projectDir.exists()) {
            telemetry.message("Project base directory: %s", projectPath);
        } else {
            description = "Project base directory doesn't exist";
            telemetry.error(description);
            return new CleanBuildMessage(ExecutionStatus.ERROR, description, projectPath);
        }

        if (buildDir.exists()) {
            telemetry.message("Build directory found: %s", buildDir.getAbsolutePath());

            try {
                FileUtils.deleteDirectory(buildDir);
                telemetry.message("Cleaning up build directory");

            } catch (IOException e) {
                description = String.format("Failed to remove build directory: %s", e.getLocalizedMessage());
                telemetry.error(description);
                return new CleanBuildMessage(ExecutionStatus.ERROR, description, projectPath);
            }
        } else {
            telemetry.message("No build directory found: %s", buildDir.getAbsolutePath());
        }

        return new CleanBuildMessage(ExecutionStatus.SUCCESS, description, projectPath);
    }
}
