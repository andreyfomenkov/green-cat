package core.task.retrolambda;

import core.command.CommandExecutor;
import core.command.CommandLineBuilder;
import core.command.Parameter;
import core.message.ClassDesugarMessage;
import core.message.CompileWithJavacMessage;
import core.task.ExecutionStatus;
import core.task.Task;
import core.task.TaskPurpose;
import core.telemetry.Telemetry;
import core.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static ui.util.Utils.isNullOrEmpty;

public class RetrolambdaTask implements Task<CompileWithJavacMessage, ClassDesugarMessage> {

    private static final String GRADLE_CACHES_MODULES_DIR = "/.gradle/caches/modules-2/";
    private static final String GRADLE_CACHES_DIR = "/.gradle/caches/";
    private static final String GRADLE_GENERIC_DIR = "/.gradle/";
    private static final String RETROLAMBDA_FILE_PATTERN = "retrolambda*.jar";
    private static final String[] JAR_FILE_LOOKUP_ORDER = {GRADLE_CACHES_MODULES_DIR, GRADLE_CACHES_DIR, GRADLE_GENERIC_DIR};
    private final String objPath;

    public RetrolambdaTask(String objPath) {
        this.objPath = objPath;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.PROCESS_WITH_RETROLAMBDA;
    }

    @Override
    public ClassDesugarMessage exec(Telemetry telemetry, CompileWithJavacMessage message) {
        String jarPath = getRetrolambdaJarPath(telemetry);

        if (isNullOrEmpty(jarPath)) {
            return new ClassDesugarMessage(ExecutionStatus.ERROR, "Couldn't find retrolambda.jar in Gradle cache");
        } else {
            telemetry.message("JAR file: %s", jarPath);
        }

        if (processClassFiles(telemetry, jarPath, objPath, message.classpath)) {
            return new ClassDesugarMessage(ExecutionStatus.SUCCESS, null);
        } else {
            return new ClassDesugarMessage(ExecutionStatus.ERROR, "Failed to process class files with Retrolambda");
        }
    }

    private @Nullable String getRetrolambdaJarPath(Telemetry telemetry) {
        String homePath = System.getProperty("user.home");

        for (String path : JAR_FILE_LOOKUP_ORDER) {
            telemetry.message("Looking for retrolambda.jar in %s...", homePath + path);
            List<String> results = Utils.findFilesRecursive(homePath + path, RETROLAMBDA_FILE_PATTERN);

            if (results.size() > 0 && results.get(0).endsWith(".jar")) {
                return results.get(0);
            }
        }

        telemetry.error("Couldn't find retrolambda.jar in Gradle cache");
        return null;
    }

    private boolean processClassFiles(Telemetry telemetry, String jarFilePath, String objPath, String classpath) {
        String cmd = CommandLineBuilder.create("java")
                .add(new Parameter("-Dretrolambda.inputDir=" + objPath))
                .add(new Parameter("-Dretrolambda.classpath=" + classpath))
                .add(new Parameter("-jar", jarFilePath))
                .build();
        List<String> output = CommandExecutor.exec(cmd);
        boolean success = false;

        for (String line : output) {
            line = line.replace("%20", " ");
            telemetry.message(line);

            if (!success && line.contains("Saving lambda class:")) {
                success = true;
            }
        }

        return success;
    }
}
