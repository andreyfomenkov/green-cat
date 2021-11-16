package ru.fomenkov.task;

import com.sun.istack.internal.NotNull;
import ru.fomenkov.message.Message;
import ru.fomenkov.telemetry.Telemetry;

public interface Task<I extends Message, O extends Message> {

    @NotNull TaskPurpose getPurpose();

    O exec(I message);
}
