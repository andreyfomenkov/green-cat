package async;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

public abstract class AsyncExecutor<R> {

    private final Application application = ApplicationManager.getApplication();
    private boolean started;

    public void start() {
        if (started) {
            throw new IllegalStateException("Can't execute multiple times");
        } else {
            started = true;
            application.executeOnPooledThread(() -> {
                R result = onBackground();
                application.runReadAction(() -> onActionComplete(result));
            });
        }
    }

    public abstract R onBackground();

    public abstract void onActionComplete(R result);
}
