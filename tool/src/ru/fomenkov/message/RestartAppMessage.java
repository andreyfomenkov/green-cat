package ru.fomenkov.message;

import ru.fomenkov.task.ExecutionStatus;

public class RestartAppMessage extends Message {

    public RestartAppMessage() {
        super(ExecutionStatus.SUCCESS, null);
    }
}
