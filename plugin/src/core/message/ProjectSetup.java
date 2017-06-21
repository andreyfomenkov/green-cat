package core.message;

import core.task.ExecutionStatus;

public class ProjectSetup extends Message {

    private final String projectPath;

    public ProjectSetup(String projectPath) {
        super(ExecutionStatus.SUCCESS, null);
        this.projectPath = projectPath;
    }

    public String getProjectPath() {
        return projectPath;
    }
}
