package ru.fomenkov.message;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.task.ExecutionStatus;

public class ClassDesugarMessage extends Message {

    public ClassDesugarMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
    }
}
