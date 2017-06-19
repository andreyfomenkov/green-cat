package core.message;

import com.sun.istack.internal.Nullable;

public class ProjectSetup extends Message {

    public ProjectSetup(@Nullable String description) {
        this(true, description);
    }

    protected ProjectSetup(boolean success, @Nullable String description) {
        super(success, description);
    }
}
