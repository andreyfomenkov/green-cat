package core.message;

import com.sun.istack.internal.Nullable;
import core.task.ExecutionStatus;

public class ClassDesugarMessage extends Message {

    public ClassDesugarMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
    }
}
