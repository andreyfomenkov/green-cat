package ru.fomenkov;

import java.io.File;
import java.util.Map;
import java.util.Set;

import ru.fomenkov.message.ModuleDiffMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.TaskExecutor;
import ru.fomenkov.task.javac.CompileWithJavacTask;

public class IncrementalBuild {

    public static class Result {

        public ExecutionStatus status;
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
        Set<File> files = diff.get(module);
        ModuleDiffMessage message = new ModuleDiffMessage(module, files);
        File compileDir = GreenCat.getCompileDir(projectPath, module.name);
        String rFilePath = getGeneratedRFilePath(module);
        TaskExecutor.Result result = TaskExecutor.create(message)
                .add(new CompileWithJavacTask(projectPath, rFilePath.isEmpty() ? classpath : rFilePath + ":" + classpath, compileDir))
                .execute();
        submitResult.status = result.status;
//        submitResult.report = result.report; TODO: report
        return submitResult;
    }
}
