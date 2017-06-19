package core.message;

import com.sun.istack.internal.Nullable;

public abstract class Message {

    public final boolean success;
    public final @Nullable String description;

    protected Message(boolean success, @Nullable String description) {
        this.success = success;
        this.description = description;
    }
}
