package ru.fomenkov.message;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.task.ExecutionStatus;

public class CleanBuildMessage extends Message {

    private final String projectPath;

    public CleanBuildMessage(ExecutionStatus status, @Nullable String description, String projectPath) {
        super(status, description);
        this.projectPath = projectPath;
    }

    public String getProjectPath() {
        return projectPath;
    }
}
