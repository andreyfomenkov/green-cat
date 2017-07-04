package core.message;

import com.sun.istack.internal.Nullable;
import core.task.ExecutionStatus;

public class RestartAppMessage extends Message {

    public RestartAppMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
    }
}
