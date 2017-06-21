package core.message;

import com.sun.istack.internal.Nullable;
import core.task.ExecutionStatus;

public abstract class Message {

    public final ExecutionStatus status;
    public final @Nullable String description;

    protected Message(ExecutionStatus status, @Nullable String description) {
        this.status = status;
        this.description = description;
    }
}
