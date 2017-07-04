package core.message;

import core.task.ExecutionStatus;

public class RestartAppMessage extends Message {

    public RestartAppMessage() {
        super(ExecutionStatus.SUCCESS, null);
    }
}
