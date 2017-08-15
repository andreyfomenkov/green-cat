package ru.fomenkov.message;

import com.google.common.collect.ImmutableList;
import com.sun.istack.internal.Nullable;
import ru.fomenkov.task.ExecutionStatus;

import java.io.File;
import java.util.List;

public class GitDiffMessage extends Message {

    private final List<File> fileList;

    public GitDiffMessage(List<File> fileList) {
        super(fileList.isEmpty() ? ExecutionStatus.TERMINATED : ExecutionStatus.SUCCESS, null);
        this.fileList = fileList;
    }

    public GitDiffMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
        this.fileList = ImmutableList.of();
    }

    public List<File> getFileList() {
        return ImmutableList.copyOf(fileList);
    }
}
