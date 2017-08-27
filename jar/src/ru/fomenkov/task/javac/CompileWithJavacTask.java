package ru.fomenkov.task.javac;

import ru.fomenkov.GreenCat;
import ru.fomenkov.Module;
import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.CompileWithJavacMessage;
import ru.fomenkov.message.ModuleDiffMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;
import java.util.List;
import java.util.Set;


public class CompileWithJavacTask implements Task<ModuleDiffMessage, CompileWithJavacMessage> {

    private final String classpath;
    private final String projectPath;
    private final File objDir;

    public CompileWithJavacTask(String projectPath, String classpath, File objDir) {
        this.projectPath = projectPath;
        this.classpath = classpath;
        this.objDir = objDir;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.COMPILE_WITH_JAVAC;
    }

    @Override
    public CompileWithJavacMessage exec(Telemetry telemetry, ModuleDiffMessage message) {
        if (message.status != ExecutionStatus.SUCCESS) {
            throw new IllegalArgumentException("Previous step execution failed");
        } else if (message.getFiles().isEmpty()) {
            throw new IllegalArgumentException("No files to compile from the previous step");
        }

        telemetry.message("Copy project .class files...");

        if (copyClassFiles(telemetry, projectPath, message.getModule())) {
            telemetry.message("Copying complete");
        } else {
            telemetry.error("Failed to copy");
            return new CompileWithJavacMessage(ExecutionStatus.ERROR, "Failed to copy project .class files");
        }

        telemetry.message("Compiling with javac...");

        if (compileWithJavac(telemetry, message.getFiles(), classpath)) {
            return new CompileWithJavacMessage(projectPath, classpath);
        } else {
            return new CompileWithJavacMessage(ExecutionStatus.ERROR, "Compilation errors");
        }
    }

    private boolean copyClassFiles(Telemetry telemetry, String projectPath, Module module) {
        if (!objDir.exists() && !objDir.mkdirs()) {
            telemetry.error("Failed to create directory for classes: %s", objDir.getPath());
            return false;
        }

        String srcPath = module.buildPath + "/.";
        String dstPath = GreenCat.getCompileDir(projectPath, module.name).getAbsolutePath();

        telemetry.message("Copying .class files...");
        telemetry.message("From: %s", srcPath);
        telemetry.message("To:   %s", dstPath);

        String cmd = CommandLineBuilder.create("cp -r")
                .add(new Parameter(srcPath))
                .add(new Parameter(dstPath))
                .build();

        List<String> output = CommandExecutor.execOnInputStream(cmd);
        for (String line : output) {
            telemetry.message(line);
        }
        return true;
    }

    private boolean compileWithJavac(Telemetry telemetry, Set<File> javaFiles, String classpath) {
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
        telemetry.message("Compilation %s", compilationSuccess ? "status" : "failed");
        return compilationSuccess;
    }
}
