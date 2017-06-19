package core.task;

import com.sun.istack.internal.NotNull;
import core.message.Message;
import core.telemetry.Telemetry;

public interface Task<I extends Message, O extends Message> {

    @NotNull TaskPurpose getPurpose();

    O exec(Telemetry telemetry, I message);
}
