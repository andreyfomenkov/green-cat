package core.message;

import com.sun.istack.internal.Nullable;
import core.task.ExecutionStatus;

public class DeployMessage extends Message {

    public DeployMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
    }
}
