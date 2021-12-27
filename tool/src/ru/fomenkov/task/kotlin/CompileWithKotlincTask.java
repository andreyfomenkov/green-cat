package ru.fomenkov.task.kotlin;

import java.io.File;
import java.util.List;
import java.util.Set;

import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.CompileWithJavacMessage;
import ru.fomenkov.message.ModuleDiffMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;


public class CompileWithKotlincTask implements Task<ModuleDiffMessage, CompileWithJavacMessage> {

    private final String classpath;
    private final String projectPath;
    private final File objDir;

    public CompileWithKotlincTask(String projectPath, String classpath, File objDir) {
        this.projectPath = projectPath;
        this.classpath = classpath;
        this.objDir = objDir;
    }

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.COMPILE_WITH_KOTLINC;
    }

    @Override
    public CompileWithJavacMessage exec(ModuleDiffMessage message) {
        if (message.status != ExecutionStatus.SUCCESS) {
            throw new IllegalArgumentException("Previous step execution failed");
        } else if (message.getKotlinFiles().isEmpty()) {
            throw new IllegalArgumentException("No files to compile from the previous step");
        }
        Telemetry.log("Compiling with kotlinc...");

        if (compileWithKotlinc(message.getKotlinFiles(), classpath)) {
            return new CompileWithJavacMessage(projectPath, classpath);
        } else {
            return new CompileWithJavacMessage(ExecutionStatus.ERROR, "Compilation errors");
        }
    }

    private boolean compileWithKotlinc(Set<File> javaFiles, String classpath) {
        StringBuilder srcBuilder = new StringBuilder();

        for (File file : javaFiles) {
            String path = file.getAbsolutePath();
            srcBuilder.append(path).append(" ");
        }

        String cmd = CommandLineBuilder.create("/Applications/Android\\ Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc")
                .add(new Parameter("-d", objDir.getAbsolutePath()))
                .add(new Parameter("-Xallow-no-source-files"))
                .add(new Parameter("-jvm-target 11"))
                .add(new Parameter("-no-reflect"))
                .add(new Parameter("-no-stdlib"))
                .add(new Parameter("-classpath", classpath))

//                .add(new Parameter("-sourcepath"))
//                .add(new Parameter("-proc:none"))
//                .add(new Parameter("-XDuseUnsharedTable=true"))
//                .add(new Parameter("-bootclasspath /Users/andrey.fomenkov/Library/Android/sdk/platforms/android-30/android.jar:/Users/andrey.fomenkov/Library/Android/sdk/build-tools/30.0.2/core-lambda-stubs.jar"))

                .add(new Parameter(srcBuilder.toString()))
                .build();

        List<String> output = CommandExecutor.execOnErrorStream(cmd);
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
                Telemetry.log(line);
            } else {
                Telemetry.err(line);
            }
        }
//        Telemetry.log("");

        if (compilationSuccess) {
            Telemetry.log("Compilation success");
        } else {
            Telemetry.err("Compilation failed");
        }
        return compilationSuccess;
    }
}
