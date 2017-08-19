package ru.fomenkov.message;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.task.ExecutionStatus;

public class ProjectSetupMessage extends Message {

    private final String projectPath;

    public ProjectSetupMessage(ExecutionStatus status, @Nullable String description, String projectPath) {
        super(status, description);
        this.projectPath = projectPath;
    }

    public String getProjectPath() {
        return projectPath;
    }
}
