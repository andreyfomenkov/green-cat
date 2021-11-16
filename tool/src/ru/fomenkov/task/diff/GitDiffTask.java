package ru.fomenkov.task.diff;

import com.google.common.collect.ImmutableList;
import com.sun.istack.internal.Nullable;
import ru.fomenkov.Module;
import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.GitDiffMessage;
import ru.fomenkov.message.ModulesResolveMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;
import java.util.*;

public class GitDiffTask implements Task<ModulesResolveMessage, GitDiffMessage> {

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.GIT_DIFF;
    }

    @Override
    public GitDiffMessage exec(ModulesResolveMessage message) {
        if (message.status != ExecutionStatus.SUCCESS) {
            throw new IllegalArgumentException("Previous step execution failed");
        }
        String projectPath = message.getProjectPath();

        // git version => git version ...
        String cmd = CommandLineBuilder.create("git version").build();
        List<String> output = CommandExecutor.execOnInputStream(cmd);
        Telemetry.log("Checking for git binary");

        if (output.size() > 0) {
            String line = output.get(0).trim();

            if (!line.startsWith("git version")) {
                GitDiffMessage result = new GitDiffMessage(ExecutionStatus.ERROR, "Git is not installed on your system");
                Telemetry.err(result.description);
                return result;
            }
        } else {
            GitDiffMessage result = new GitDiffMessage(ExecutionStatus.ERROR, "Git is not installed on your system");
            Telemetry.err(result.description);
            return result;
        }
        // git log -1 => commit ...
        Telemetry.log("Checking for git project");

        cmd = CommandLineBuilder.create("git")
                .add(new Parameter("-C", projectPath))
                .add(new Parameter("log", "-1"))
                .build();
        output = CommandExecutor.execOnInputStream(cmd);

        if (output.size() > 0) {
            String line = output.get(0).trim();

            if (!line.startsWith("commit")) {
                GitDiffMessage result = new GitDiffMessage(ExecutionStatus.ERROR, "Not a git repository");
                Telemetry.err(result.description);
                return result;
            }
        } else {
            GitDiffMessage result = new GitDiffMessage(ExecutionStatus.ERROR, "Not a git repository");
            Telemetry.err(result.description);
            return result;
        }
        // git status => modified: ... / untracked: ...
        Telemetry.log("Checking project changes for incremental build");

        cmd = CommandLineBuilder.create("git")
                .add(new Parameter("-C", projectPath))
                .add(new Parameter("status"))
                .build();
        output = CommandExecutor.execOnInputStream(cmd);

        List<File> modifiedFiles = new ArrayList<>();
        List<File> untrackedFiles = new ArrayList<>();
        boolean checkUntracked = false;

        for (String line : output) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("Untracked files:")) {
                checkUntracked = true;
                continue;
            }
            if (checkUntracked) {
                String path = line.trim();
                File file = new File(projectPath + "/" + path);

                if (file.exists()) {
                    untrackedFiles.add(file);
                }
            } else {
                if (line.startsWith("modified:")) {
                    String path = line.replace("modified:", "").trim();
                    File file = new File(projectPath + "/" + path);

                    if (file.exists()) {
                        modifiedFiles.add(file);
                    }
                }
            }
        }
        boolean hasUnsupportedFiles = false;

        if (!modifiedFiles.isEmpty() || !untrackedFiles.isEmpty()) {
            if (modifiedFiles.size() > 0) {
                Telemetry.log("");
                Telemetry.log("Modified files:");

                for (File file : modifiedFiles) {
                    String path = file.getAbsolutePath().replace(projectPath, "");

                    if (isSupportedFileFormat(file)) {
                        Module module = getSourceFileModule(message.getProjectModules(), file.getAbsolutePath());

                        if (module == null) {
                            Telemetry.err("Can't find module for source file: %s", path);
                            return new GitDiffMessage(ExecutionStatus.ERROR, "Can't find module for source file");
                        }
                        Telemetry.log("* %s [%s]", path, module.name);
                    } else {
                        Telemetry.err("* %s [!]", path);
                        hasUnsupportedFiles = true;
                    }
                }
            }

            if (untrackedFiles.size() > 0) {
                if (modifiedFiles.size() > 0) {
                    Telemetry.log("");
                }
                Telemetry.log("Untracked files:");

                for (File file : untrackedFiles) {
                    String path = file.getAbsolutePath().replace(projectPath, "");

                    if (isSupportedFileFormat(file)) {
                        Telemetry.log("+ %s", path);
                    } else {
                        Telemetry.err("+ %s [!]", path);
                        hasUnsupportedFiles = true;
                    }
                }
            }
        }
        if (hasUnsupportedFiles) {
            Telemetry.err("");
            Telemetry.err("WARNING: files marked with [!] are not currently supported for incremental build");
        }
        List<File> allFileList = new ArrayList<>(modifiedFiles);
        allFileList.addAll(untrackedFiles);
        List<File> sourceFileList = filterSupportedFiles(allFileList);

        if (sourceFileList.isEmpty()) {
            Telemetry.log("");
            Telemetry.log("No source changes to compile");
        }
        Map<Module, Set<File>> filesMap = new HashMap<>();

        for (File file : sourceFileList) {
            Module module = getSourceFileModule(message.getProjectModules(), file.getAbsolutePath());
            Set<File> files = filesMap.computeIfAbsent(module, k -> new HashSet<>());
            files.add(file);
        }
        return new GitDiffMessage(filesMap);
    }

    @Nullable
    private Module getSourceFileModule(List<Module> modules, String path) {
        for (Module module : modules) {
            if (path.startsWith(module.modulePath + "/")) {
                return module;
            }
        }
        return null;
    }

    private List<File> filterSupportedFiles(List<File> in) {
        List<File> files = new ArrayList<>();

        for (File file : in) {
            if (isSupportedFileFormat(file)) {
                files.add(file);
            }
        }

        return ImmutableList.copyOf(files);
    }

    private boolean isSupportedFileFormat(File file) {
        String path = file.getAbsolutePath();
        return path.endsWith(".java");
    }
}
