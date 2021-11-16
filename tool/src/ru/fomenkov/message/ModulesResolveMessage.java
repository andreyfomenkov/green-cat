package ru.fomenkov.message;

import ru.fomenkov.Module;
import ru.fomenkov.task.ExecutionStatus;

import java.util.List;

public class ModulesResolveMessage extends Message {

    private final String projectPath;
    private final List<Module> modules;

    public ModulesResolveMessage(String projectPath, List<Module> modules) {
        super(ExecutionStatus.SUCCESS, null);
        this.projectPath = projectPath;
        this.modules = modules;
    }

    public ModulesResolveMessage(ExecutionStatus status, String description) {
        super(status, description);
        this.projectPath = null;
        this.modules = null;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public List<Module> getProjectModules() {
        return modules;
    }
}
