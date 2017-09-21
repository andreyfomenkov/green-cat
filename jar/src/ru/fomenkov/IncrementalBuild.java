package ru.fomenkov;

import ru.fomenkov.message.ModuleDiffMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.TaskExecutor;
import ru.fomenkov.task.javac.CompileWithJavacTask;
import ru.fomenkov.task.retrolambda.RetrolambdaTask;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class IncrementalBuild {

    public static class Result {

        public ExecutionStatus status;
        public Telemetry telemetry;
    }

    private final String projectPath;
    private final String classpath;
    private final Module module;
    private final Map<Module, Set<File>> diff;

    public IncrementalBuild(String projectPath, String classpath, Module module, Map<Module, Set<File>> diff) {
        this.projectPath = projectPath;
        this.classpath = classpath;
        this.module = module;
        this.diff = diff;
    }

    private String getGeneratedRFilePath(Module module) {
        String path = String.format("%s/build/generated/source/r/%s", module.modulePath, module.variant);
        return new File(path).exists() ? path : "";
    }

    public Result submit() {
        Result submitResult = new Result();
        Telemetry moduleReport = new Telemetry();
        Set<File> files = diff.get(module);
        ModuleDiffMessage message = new ModuleDiffMessage(module, files);
        File compileDir = GreenCat.getCompileDir(projectPath, module.name);
        File lambdaDir = GreenCat.getLambdaDir(projectPath);
        String rFilePath = getGeneratedRFilePath(module);

        TaskExecutor.Result result = TaskExecutor.create(message, moduleReport)
                .add(new CompileWithJavacTask(projectPath, rFilePath.isEmpty() ? classpath : rFilePath + ":" + classpath, compileDir))
                .add(new RetrolambdaTask(compileDir.getAbsolutePath(), lambdaDir.getAbsolutePath()))
                .execute();

        submitResult.status = result.status;
        submitResult.telemetry = moduleReport;
        return submitResult;
    }
}
