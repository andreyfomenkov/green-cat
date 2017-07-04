package core.task.javac;

import com.intellij.ide.macro.ClasspathMacro;
import com.intellij.openapi.actionSystem.DataContext;
import core.command.CommandExecutor;
import core.command.CommandLineBuilder;
import core.command.Parameter;
import core.exception.ClasspathException;
import core.message.CompileWithJavacMessage;
import core.message.GitDiffMessage;
import core.task.ExecutionStatus;
import core.task.Task;
import core.task.TaskPurpose;
import core.telemetry.Telemetry;

import java.io.File;
import java.util.List;

import static ui.util.Utils.isNullOrEmpty;

public class CompileWithJavac implements Task<GitDiffMessage, CompileWithJavacMessage> {

    private final DataContext context;
    private final File objDir;

    public CompileWithJavac(DataContext context, File objDir) {
        this.context = context;
        this.objDir = objDir;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.COMPILE_WITH_JAVAC;
    }

    @Override
    public CompileWithJavacMessage exec(Telemetry telemetry, GitDiffMessage message) {
        if (message.status != ExecutionStatus.SUCCESS) {
            throw new IllegalArgumentException("Previous step execution failed");
        } else if (message.getFileList().isEmpty()) {
            throw new IllegalArgumentException("No files to compile from the previous step");
        }

        telemetry.message("Compiling with javac...");
        String classpath;

        try {
            classpath = composeClasspathArgument(context);
        } catch (ClasspathException e) {
            return new CompileWithJavacMessage(ExecutionStatus.ERROR, "Classpath is empty");
        }

        if (compileWithJavac(telemetry, message.getFileList(), classpath)) {
            return new CompileWithJavacMessage(classpath);
        } else {
            return new CompileWithJavacMessage(ExecutionStatus.ERROR, "Compilation errors");
        }
    }

    private String composeClasspathArgument(DataContext context) throws ClasspathException {
        ClasspathMacro macro = new ClasspathMacro();
        String value = macro.expand(context);

        if (isNullOrEmpty(value)) {
            throw new ClasspathException("Classpath is empty");
        }

        StringBuilder builder = new StringBuilder();

        for (String path : value.split(":")) {
            File file = new File(path);

            if (file.exists()) {
                path = path.replace(" ", "%20");
                builder.append(builder.length() == 0 ? "" : ":").append(path);
            }
        }

        return builder.toString();
    }

    private boolean compileWithJavac(Telemetry telemetry, List<File> javaFiles, String classpath) {
        String cmd = CommandLineBuilder.create("which javac").build();
        List<String> output = CommandExecutor.exec(cmd);

        if (output.isEmpty()) {
            telemetry.error("Can't find java compiler");
            return false;
        }

        if (!objDir.exists() && !objDir.mkdirs()) {
            telemetry.error("Failed to create directory for classes: %s", objDir.getPath());
            return false;
        }

        StringBuilder srcBuilder = new StringBuilder();

        for (File file : javaFiles) {
            String path = file.getAbsolutePath();
            srcBuilder.append(path).append(" ");
        }

        cmd = CommandLineBuilder.create("javac")
                .add(new Parameter("-cp", classpath))
                .add(new Parameter("-d", objDir.getAbsolutePath()))
                .add(new Parameter(srcBuilder.toString()))
                .build();

        output = CommandExecutor.exec(cmd);
        boolean compilationSuccess = true;

        for (String line : output) {
            if (line.contains("error: ") || line.contains("invalid flag:")) {
                compilationSuccess = false;
                break;
            }
        }

        for (String line : output) {
            line = line.replace("%", "");

            if (compilationSuccess) {
                telemetry.message(line);
            } else {
                telemetry.error(line);
            }
        }

        telemetry.message("");
        telemetry.message("Compilation %s", compilationSuccess ? "success" : "failed");
        return compilationSuccess;
    }
}
