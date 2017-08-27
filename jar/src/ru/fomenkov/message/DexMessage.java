package ru.fomenkov.message;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.task.ExecutionStatus;

public class DexMessage extends Message {

    public DexMessage() {
        super(ExecutionStatus.SUCCESS, null);
    }

    public DexMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
    }
}
