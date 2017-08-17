package ru.fomenkov.task.diff;

import com.google.common.collect.ImmutableList;
import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.message.CleanBuildMessage;
import ru.fomenkov.message.GitDiffMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GitDiff implements Task<CleanBuildMessage, GitDiffMessage> {

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.GIT_DIFF;
    }

    @Override
    public GitDiffMessage exec(Telemetry telemetry, CleanBuildMessage message) {
        if (message.status != ExecutionStatus.SUCCESS) {
            throw new IllegalArgumentException("Previous step execution failed");
        }

        String projectPath = message.getProjectPath();

        // git version => git version ...
        String cmd = CommandLineBuilder.create("git version").build();
        List<String> output = CommandExecutor.exec(cmd, false);
        telemetry.message("Checking for git binary");

        if (output.size() > 0) {
            String line = output.get(0).trim();

            if (!line.startsWith("git version")) {
                GitDiffMessage result = new GitDiffMessage(ExecutionStatus.ERROR, "Git is not installed on your system");
                telemetry.error(result.description);
                return result;
            }
        } else {
            GitDiffMessage result = new GitDiffMessage(ExecutionStatus.ERROR, "Git is not installed on your system");
            telemetry.error(result.description);
            return result;
        }

        // git log -1 => commit ...
        telemetry.message("Checking for git project");
        cmd = CommandLineBuilder.create("git")
                .add(new Parameter("-C", projectPath))
                .add(new Parameter("log", "-1"))
                .build();
        output = CommandExecutor.exec(cmd, false);

        if (output.size() > 0) {
            String line = output.get(0).trim();

            if (!line.startsWith("commit")) {
                GitDiffMessage result = new GitDiffMessage(ExecutionStatus.ERROR, "Not a git repository");
                telemetry.error(result.description);
                return result;
            }
        } else {
            GitDiffMessage result = new GitDiffMessage(ExecutionStatus.ERROR, "Not a git repository");
            telemetry.error(result.description);
            return result;
        }

        // git status => modified: ... / untracked: ...
        telemetry.message("Checking project changes for incremental build");
        cmd = CommandLineBuilder.create("git")
                .add(new Parameter("-C", projectPath))
                .add(new Parameter("status"))
                .build();
        output = CommandExecutor.exec(cmd, false);

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
                telemetry.message("");
                telemetry.message("Modified files:");

                for (File file : modifiedFiles) {
                    String path = file.getAbsolutePath().replace(projectPath, "");

                    if (isSupportedFileFormat(file)) {
                        telemetry.message("* %s", path);
                    } else {
                        telemetry.warn("* %s [!]", path);
                        hasUnsupportedFiles = true;
                    }
                }
            }

            if (untrackedFiles.size() > 0) {
                if (modifiedFiles.size() > 0) {
                    telemetry.message("");
                }
                telemetry.message("Untracked files:");

                for (File file : untrackedFiles) {
                    String path = file.getAbsolutePath().replace(projectPath, "");

                    if (isSupportedFileFormat(file)) {
                        telemetry.message("+ %s", path);
                    } else {
                        telemetry.warn("+ %s [!]", path);
                        hasUnsupportedFiles = true;
                    }
                }
            }
        }

        if (hasUnsupportedFiles) {
            telemetry.warn("");
            telemetry.warn("WARNING: files marked with [!] are not currently supported for incremental build");
            telemetry.warn("XML resources processing (layouts, strings, etc.) is planned for the upcoming versions");
        }

        List<File> allFileList = new ArrayList<>(modifiedFiles);
        allFileList.addAll(untrackedFiles);
        List<File> sourceFileList = filterSupportedFiles(allFileList);

        if (sourceFileList.isEmpty()) {
            telemetry.message("");
            telemetry.message("No source changes to compile");
        }

        return new GitDiffMessage(sourceFileList);
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
