package ru.fomenkov.message;

import com.google.common.collect.ImmutableMap;
import com.sun.istack.internal.Nullable;
import ru.fomenkov.Module;
import ru.fomenkov.task.ExecutionStatus;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GitDiffMessage extends Message {

    private final Map<Module, Set<File>> files;

    public GitDiffMessage(Map<Module, Set<File>> files) {
        super(files.isEmpty() ? ExecutionStatus.TERMINATED : ExecutionStatus.SUCCESS, null);
        this.files = files;
    }

    public GitDiffMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
        this.files = new HashMap<>();
    }

    public Map<Module, Set<File>> getFiles() {
        return ImmutableMap.copyOf(files);
    }
}
