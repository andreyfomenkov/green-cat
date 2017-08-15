package ru.fomenkov.message;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.task.ExecutionStatus;

public class CompileWithJavacMessage extends Message {

    @Nullable
    public final String classpath;

    public CompileWithJavacMessage(String classpath) {
        super(ExecutionStatus.SUCCESS, null);
        this.classpath = classpath;
    }

    public CompileWithJavacMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
        this.classpath = null;
    }
}
