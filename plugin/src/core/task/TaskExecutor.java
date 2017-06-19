package core.task;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import core.message.Message;
import core.telemetry.Telemetry;
import core.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TaskExecutor {

    public static class Result {

        public final boolean success;
        public final Task task;
        public final @Nullable String description;

        public Result(Task task, @Nullable String description, boolean success) {
            this.task = task;
            this.description = description;
            this.success = success;
        }
    }

    public static TaskExecutor create(Message startupMessage, Telemetry telemetry) {
        return new TaskExecutor(startupMessage, telemetry);
    }

    private final Message startupMessage;
    private final Telemetry telemetry;
    private final List<Task> taskList = new ArrayList<>();

    private TaskExecutor(@NotNull Message startupMessage, Telemetry telemetry) {
        this.startupMessage = startupMessage;
        this.telemetry = telemetry;
    }

    public TaskExecutor add(Task... tasks) {
        taskList.addAll(Arrays.asList(tasks));
        return this;
    }

    public Result execute() {
        Message message = startupMessage;
        int size = taskList.size();
        long startTime = System.nanoTime();

        telemetry.clear();
        telemetry.green("* * * * * * * * * * * * * * *");
        telemetry.green("* GREENCAT TELEMETRY REPORT *");
        telemetry.green("* * * * * * * * * * * * * * *");

        for (int i = 0; i < size; i++) {
            Task task = taskList.get(i);

            try {
                TaskPurpose purpose = task.getPurpose();
                telemetry.green("");
                telemetry.green("[STEP %d/%d] %s: %s", i + 1, size, purpose, purpose.value());
                message = task.exec(telemetry, message);

                if (!message.success) {
                    telemetry.error("");
                    telemetry.error("TASK EXECUTION FAILED");
                    return new Result(task, message.description, false);
                }
            } catch (ClassCastException e) {
                telemetry.error("");
                telemetry.error("TASK EXECUTION FAILED: task message types mismatch");
                return new Result(task, "task message types mismatch", false);

            } catch (Exception e) {
                telemetry.error("");
                telemetry.error("TASK EXECUTION FAILED");
                return new Result(task, String.format("error: %s", e.getLocalizedMessage()), false);
            }
        }

        long endTime = System.nanoTime();
        String delay = Utils.formatNanoTimeToSeconds(endTime - startTime);
        telemetry.green("");
        telemetry.green("TASK(S) EXECUTION COMPLETE IN %s SEC", delay);
        return new Result(taskList.get(size - 1), null, true);
    }
}
