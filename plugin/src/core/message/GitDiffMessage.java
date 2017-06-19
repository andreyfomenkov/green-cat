package core.message;

import com.sun.istack.internal.Nullable;

public class GitDiffMessage extends Message {

    public GitDiffMessage(boolean success, @Nullable String description) {
        super(success, description);
    }
}
