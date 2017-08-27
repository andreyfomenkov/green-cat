package ru.fomenkov.message;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.task.ExecutionStatus;

public class CompileWithJavacMessage extends Message {

    @Nullable
    public final String classpath;
    public final String projectPath;

    public CompileWithJavacMessage(String projectPath, String classpath) {
        super(ExecutionStatus.SUCCESS, null);
        this.projectPath = projectPath;
        this.classpath = classpath;
    }

    public CompileWithJavacMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
        this.projectPath = null;
        this.classpath = null;
    }
}
