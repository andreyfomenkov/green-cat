package ru.fomenkov.task;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import ru.fomenkov.message.Message;
import ru.fomenkov.telemetry.Telemetry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TaskExecutor {

    public static class Result {

        public final ExecutionStatus status;
        public final Task task;
        public final Message message;
        public final @Nullable String description;
        public final long nanoTime;

        public Result(Task task, Message message,  @Nullable String description, ExecutionStatus status) {
            this(task, message, description, status, -1);
        }

        public Result(Task task, Message message, @Nullable String description, ExecutionStatus status, long nanoTime) {
            this.task = task;
            this.description = description;
            this.message = message;
            this.status = status;
            this.nanoTime = nanoTime;
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

        for (int i = 0; i < size; i++) {
            Task task = taskList.get(i);

            try {
                TaskPurpose purpose = task.getPurpose();
                telemetry.green("");
                telemetry.green("[STEP %d/%d] %s: %s", i + 1, size, purpose, purpose.value());

                long start = System.currentTimeMillis();
                message = task.exec(telemetry, message);
                long delay = System.currentTimeMillis() - start;

                if (delay < 1000) {
                    telemetry.message("TOOK %d ms", delay);
                } else {
                    telemetry.message("TOOK %d s", delay / 1000);
                }

                if (message.status == ExecutionStatus.ERROR) {
                    telemetry.error("");
                    telemetry.error("TASK EXECUTION FAILED");
                    return new Result(task, message, message.description, message.status);

                } else if (message.status == ExecutionStatus.TERMINATED) {
                    telemetry.warn("");
                    telemetry.warn("TASK EXECUTION TERMINATED");
                    return new Result(task, message, message.description, message.status);
                }
            } catch (ClassCastException e) {
                telemetry.error("");
                telemetry.error("TASK EXECUTION FAILED: task message types mismatch");
                return new Result(task, message, "task message types mismatch", ExecutionStatus.ERROR);

            } catch (Exception e) {
                telemetry.error("");
                telemetry.error("TASK EXECUTION FAILED: %s", e.getLocalizedMessage());
                return new Result(task, message, String.format("error: %s", e.getLocalizedMessage()), ExecutionStatus.ERROR);
            }
        }

        long endTime = System.nanoTime();
        telemetry.green("");
        return new Result(taskList.get(size - 1), message, null, ExecutionStatus.SUCCESS, endTime - startTime);
    }
}
