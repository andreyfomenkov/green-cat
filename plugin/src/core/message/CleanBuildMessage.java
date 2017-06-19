package core.message;

import com.sun.istack.internal.Nullable;

public class CleanBuildMessage extends Message {

    public CleanBuildMessage(String projectDir) {
        super(true, null);
    }

    public CleanBuildMessage(boolean success, @Nullable String description) {
        super(success, description);
    }
}
