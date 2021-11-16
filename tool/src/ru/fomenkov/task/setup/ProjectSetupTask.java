package ru.fomenkov.task.setup;

import com.sun.istack.internal.NotNull;
import org.apache.commons.io.FileUtils;
import ru.fomenkov.GreenCat;
import ru.fomenkov.message.ProjectSetupMessage;
import ru.fomenkov.message.ProjectSetup;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;
import java.io.IOException;

public class ProjectSetupTask implements Task<ProjectSetup, ProjectSetupMessage> {

    @Override
    @NotNull
    public TaskPurpose getPurpose() {
        return TaskPurpose.SETUP_PROJECT;
    }

    @Override
    public ProjectSetupMessage exec(ProjectSetup message) {
        String projectPath = message.getProjectPath();
        File projectDir = new File(projectPath);
        File buildDir = GreenCat.getBuildDir(projectPath);
        String description = null;

        if (projectDir.exists()) {
            Telemetry.log("Project base directory: %s", projectPath);
        } else {
            description = "Project base directory doesn't exist";
            Telemetry.err(description);
            return new ProjectSetupMessage(ExecutionStatus.ERROR, description, projectPath);
        }

        if (buildDir.exists()) {
            Telemetry.log("Build directory found: %s", buildDir.getAbsolutePath());

            try {
                FileUtils.deleteDirectory(buildDir);
                Telemetry.log("Cleaning up build directory");

            } catch (IOException e) {
                description = String.format("Failed to remove build directory: %s", e.getLocalizedMessage());
                Telemetry.err(description);
                return new ProjectSetupMessage(ExecutionStatus.ERROR, description, projectPath);
            }
        } else {
            Telemetry.log("No build directory found: %s", buildDir.getAbsolutePath());
        }
        return new ProjectSetupMessage(ExecutionStatus.SUCCESS, description, projectPath);
    }
}
