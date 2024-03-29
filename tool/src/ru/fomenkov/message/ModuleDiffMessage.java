package ru.fomenkov.message;

import ru.fomenkov.Module;
import ru.fomenkov.task.ExecutionStatus;

import java.io.File;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    public Set<File> getJavaFiles() {
        return files.stream().filter(file -> file.getAbsolutePath().endsWith(".java")).collect(Collectors.toSet());
    }

    public Set<File> getKotlinFiles() {
        return files.stream().filter(file -> file.getAbsolutePath().endsWith(".kt")).collect(Collectors.toSet());
    }
}
