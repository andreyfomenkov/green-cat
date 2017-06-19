package core.task.cleanup;

import com.sun.istack.internal.NotNull;
import core.GreenCat;
import core.message.CleanBuildMessage;
import core.message.ProjectSetup;
import core.task.Task;
import core.task.TaskPurpose;
import core.telemetry.Telemetry;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class CleanupTask implements Task<ProjectSetup, CleanBuildMessage> {

    private final String projectPath;

    public CleanupTask(String projectPath) {
        this.projectPath = projectPath;
    }

    @Override
    @NotNull
    public TaskPurpose getPurpose() {
        return TaskPurpose.CLEANUP_BUILD;
    }

    @Override
    public CleanBuildMessage exec(Telemetry telemetry, ProjectSetup message) {
        File projectDir = new File(projectPath);
        File buildDir = GreenCat.getBuildPath(projectPath);
        String description = null;
        boolean success = true;

        if (projectDir.exists()) {
            telemetry.message("Project base directory: %s", projectPath);
        } else {
            description = "Project base directory doesn't exist";
            success = false;
            telemetry.error(description);
        }

        if (buildDir.exists()) {
            telemetry.message("Build directory found: %s", buildDir.getAbsolutePath());

            try {
                FileUtils.deleteDirectory(buildDir);
                telemetry.message("Cleaning up build directory");
            } catch (IOException e) {
                success = false;
                description = e.getLocalizedMessage();
                telemetry.error("Failed to remove build directory: %s", description);
            }
        } else {
            telemetry.message("No build directory found: %s", buildDir.getAbsolutePath());
        }

        return new CleanBuildMessage(success, description);
    }
}
