package core.message;

import com.sun.istack.internal.Nullable;
import core.task.ExecutionStatus;

public class CompileWithJavacMessage extends Message {

    public CompileWithJavacMessage(ExecutionStatus status, @Nullable String description) {
        super(status, description);
    }
}
