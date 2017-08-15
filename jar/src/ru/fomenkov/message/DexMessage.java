package ru.fomenkov.message;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.task.ExecutionStatus;

public class DexMessage extends Message {

    public final String dexFilePath;

    public DexMessage(String dexFilePath) {
        super(ExecutionStatus.SUCCESS, null);
        this.dexFilePath = dexFilePath;
    }

    public DexMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
        this.dexFilePath = null;
    }
}
