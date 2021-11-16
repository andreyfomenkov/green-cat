package ru.fomenkov.message;

import ru.fomenkov.Module;
import ru.fomenkov.task.ExecutionStatus;

import java.io.File;
import java.util.Set;

public class ModuleDiffMessage extends Message {

    private final Module module;
    private final Set<File> files;

    public ModuleDiffMessage(Module module, Set<File> files) {
        super(ExecutionStatus.SUCCESS, null);
        this.module = module;
        this.files = files;
    }

    public Module getModule() {
        return module;
    }

    public Set<File> getFiles() {
        return files;
    }
}
