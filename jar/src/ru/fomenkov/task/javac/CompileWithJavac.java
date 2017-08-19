package ru.fomenkov.task.javac;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.CompileWithJavacMessage;
import ru.fomenkov.message.GitDiffMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;
import java.util.List;


public class CompileWithJavac implements Task<GitDiffMessage, CompileWithJavacMessage> {

    private final String classpath;
    private final String projectPath;
    private final File objDir;

    public CompileWithJavac(String projectPath, String classpath, File objDir) {
        this.projectPath = projectPath;
        this.classpath = classpath;
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

        telemetry.message("Copy project .class files...");

        if (copyClassFiles(telemetry)) {
            telemetry.message("Copying complete");
        } else {
            telemetry.error("Failed to copy");
            return new CompileWithJavacMessage(ExecutionStatus.ERROR, "Failed to copy project .class files");
        }

        telemetry.message("Compiling with javac...");

        if (compileWithJavac(telemetry, message.getFileList(), classpath)) {
            return new CompileWithJavacMessage(classpath);
        } else {
            return new CompileWithJavacMessage(ExecutionStatus.ERROR, "Compilation errors");
        }
    }

    private boolean copyClassFiles(Telemetry telemetry) {
        if (!objDir.exists() && !objDir.mkdirs()) {
            telemetry.error("Failed to create directory for classes: %s", objDir.getPath());
            return false;
        }

        String cmd = CommandLineBuilder.create("cp -r")
                .add(new Parameter("/home/afomenkov/workspace/client-android/presentation/app/build/intermediates/classes/googlePlayStoreAgoda/debug/."))
                .add(new Parameter("/home/afomenkov/workspace/client-android/build/greencat/compile"))
                .build();

        List<String> output = CommandExecutor.execOnInputStream(cmd);
        for (String line : output) {
            telemetry.message(line);
        }
        return true;
    }

    private boolean compileWithJavac(Telemetry telemetry, List<File> javaFiles, String classpath) {
        String cmd = CommandLineBuilder.create("which javac").build();
        List<String> output = CommandExecutor.execOnInputStream(cmd);

        if (output.isEmpty()) {
            telemetry.error("Can't find java compiler");
            return false;
        }

        StringBuilder srcBuilder = new StringBuilder();

        for (File file : javaFiles) {
            String path = file.getAbsolutePath();
            srcBuilder.append(path).append(" ");
        }

        cmd = CommandLineBuilder.create("javac")
                .add(new Parameter("-d", objDir.getAbsolutePath()))
                .add(new Parameter("-source 1.8"))
                .add(new Parameter("-target 1.8"))
                .add(new Parameter("-encoding UTF-8"))
                .add(new Parameter("-g"))
                .add(new Parameter("-cp", classpath))
                .add(new Parameter(srcBuilder.toString()))
                .build();

        output = CommandExecutor.execOnErrorStream(cmd);
        boolean compilationSuccess = true;

        for (String line : output) {
            if (line.contains("error: ") || line.contains("invalid flag:") || line.contains("Usage: javac")) {
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
