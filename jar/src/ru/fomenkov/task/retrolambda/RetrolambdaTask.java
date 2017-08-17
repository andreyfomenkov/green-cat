package ru.fomenkov.task.retrolambda;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.ClassDesugarMessage;
import ru.fomenkov.message.CompileWithJavacMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;
import ru.fomenkov.util.Utils;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;

public class RetrolambdaTask implements Task<CompileWithJavacMessage, ClassDesugarMessage> {

    private static final String GRADLE_CACHES_MODULES_DIR = "/.gradle/caches/modules-2/";
    private static final String GRADLE_CACHES_DIR = "/.gradle/caches/";
    private static final String GRADLE_GENERIC_DIR = "/.gradle/";
    private static final String RETROLAMBDA_FILE_PATTERN = "retrolambda*.jar";
    private static final String[] JAR_FILE_LOOKUP_ORDER = {GRADLE_CACHES_MODULES_DIR, GRADLE_CACHES_DIR, GRADLE_GENERIC_DIR};
    private final String compilePath;
    private final String lambdaPath;

    public RetrolambdaTask(String compilePath, String lambdaPath) {
        this.compilePath = compilePath;
        this.lambdaPath = lambdaPath;
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

        if (processClassFiles(telemetry, jarPath, compilePath, lambdaPath, message.classpath)) {
            return new ClassDesugarMessage(ExecutionStatus.SUCCESS, null);
        } else {
            return new ClassDesugarMessage(ExecutionStatus.ERROR, "Failed to process class files with Retrolambda");
        }
    }

    @Nullable
    private String getRetrolambdaJarPath(Telemetry telemetry) {
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

    private boolean processClassFiles(Telemetry telemetry, String jarFilePath, String compilePath, String lambdaPath, String classpath) {
        String cmd = CommandLineBuilder.create("java")
                .add(new Parameter("-Dretrolambda.bytecodeVersion=50"))
                .add(new Parameter("-Dretrolambda.inputDir=" + compilePath))
                .add(new Parameter("-Dretrolambda.outputDir=" + lambdaPath))
                .add(new Parameter("-Dretrolambda.classpath=" + compilePath + ":" + classpath))
                .add(new Parameter("-Dfile.encoding=UTF-8"))
                .add(new Parameter("-Duser.country=US"))
                .add(new Parameter("-Duser.language=en"))
                .add(new Parameter("-Duser.variant"))
                .add(new Parameter("-jar", jarFilePath))
                .build();

        List<String> output = CommandExecutor.exec(cmd, false);
        boolean success = true;

        for (String line : output) {
            line = line.trim();

            if (line.startsWith("Saving") || line.startsWith("Classpath") ) {
                continue;
            } else if (line.startsWith("Error!")) {
                success = false;
            }
            telemetry.message(line);
        }
        return success;
    }
}
