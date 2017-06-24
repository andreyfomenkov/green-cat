package core.task.javac;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import core.GreenCat;
import core.command.CommandExecutor;
import core.command.CommandLineBuilder;
import core.command.Parameter;
import core.message.CompileWithJavacMessage;
import core.message.GitDiffMessage;
import core.task.ExecutionStatus;
import core.task.Task;
import core.task.TaskPurpose;
import core.telemetry.Telemetry;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.compiler.CompilerPaths.getModuleOutputPath;

public class CompileWithJavac implements Task<GitDiffMessage, CompileWithJavacMessage> {

    private final Project project;
    private final File objDir;

    public CompileWithJavac(Project project, File objDir) {
        this.project = project;
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

        ModuleManager manager = ModuleManager.getInstance(project);
        Module[] modules = manager.getModules();
        Set<File> projectClasspath = new HashSet<>();
        telemetry.message("Project modules:");

        for (Module module : modules) {
            List<File> moduleClasspath = getModuleClasspath(module, false);
            projectClasspath.addAll(moduleClasspath);
            telemetry.message("- %s: %d classpath items", module.getName(), moduleClasspath.size());
        }

        telemetry.message("Total project classpath: %d items", projectClasspath.size());
        telemetry.message("");
        telemetry.message("Compiling with javac...");

        if (compileWithJavac(telemetry, message.getFileList(), new ArrayList<>(projectClasspath))) {
            return new CompileWithJavacMessage(ExecutionStatus.SUCCESS, null);
        } else {
            return new CompileWithJavacMessage(ExecutionStatus.ERROR, "Compilation errors");
        }
    }

    private List<File> getModuleClasspath(Module module, boolean forTestClasses) {
        List<File> fileList = new ArrayList<>();
        Set<String> pathSet = new HashSet<>();

        String moduleOutputPath = getModuleOutputPath(module, forTestClasses);
        VirtualFile[] virtualFiles = OrderEnumerator.orderEntries(module).recursively().getClassesRoots();
        pathSet.add(moduleOutputPath);

        for(VirtualFile virtualFile : virtualFiles) {
            String path = virtualFile.getPath();

            if (path.endsWith("!/")) {
                path = path.substring(0, path.length() - 2);
            } else if (path.endsWith("!")) {
                path = path.substring(0, path.length() - 1);
            }

            if (pathSet.add(path)) {
                File file = new File(path);

                if (file.exists()) {
                    fileList.add(file);
                }
            }
        }

        return fileList;
    }

    private boolean compileWithJavac(Telemetry telemetry, List<File> javaFiles, List<File> classpath) {
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
        StringBuilder cpBuilder = new StringBuilder();
        List<String> cpPaths = new ArrayList<>();

        for (File file : javaFiles) {
            String path = file.getAbsolutePath();
            srcBuilder.append(path).append(" ");
        }

        for (int i = 0; i < classpath.size(); i++) {
            String path = classpath.get(i).getAbsolutePath();

            if (path.contains(" ")) { // FIXME
                continue;
            }

            cpPaths.add(path);
            cpBuilder.append(i > 0 ? File.pathSeparator : "").append(path);
        }

        String projectPath = project.getBasePath();
        File dumpFile = GreenCat.getClasspathDumpFilePath(projectPath);
        telemetry.message("Dumping project classpath...");

        if (dumpProjectClasspath(dumpFile, cpPaths)) {
            telemetry.message("See project classpath in %s", dumpFile.getAbsolutePath());
        } else {
            telemetry.warn("Failed to write project classpath into %s", dumpFile.getAbsolutePath());
        }

        cmd = CommandLineBuilder.create("javac")
                .add(new Parameter("-cp", cpBuilder.toString()))
                .add(new Parameter("-d", objDir.getAbsolutePath()))
                .add(new Parameter(srcBuilder.toString()))
                .build();

        output = CommandExecutor.exec(cmd);
        boolean compilationSuccess = true;

        for (String line : output) {
            if (line.contains("error: ")) {
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

    private boolean dumpProjectClasspath(File dumpFile, List<String> classpath) {
        if (dumpFile.exists()) {
            if (!dumpFile.delete()) {
                return false;
            }
        }

        try {
            FileUtils.writeLines(dumpFile, classpath);
        } catch (IOException ignore) {
            return false;
        }

        return true;
    }
}
