package ru.fomenkov.task.cleanup;

import com.sun.istack.internal.NotNull;
import org.apache.commons.io.FileUtils;
import ru.fomenkov.GreenCat;
import ru.fomenkov.message.CleanBuildMessage;
import ru.fomenkov.message.ProjectSetup;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

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
        File buildDir = GreenCat.getBuildDir(projectPath);
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
