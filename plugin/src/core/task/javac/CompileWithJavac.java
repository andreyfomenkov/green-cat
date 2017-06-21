package core.task.javac;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import core.message.CompileWithJavacMessage;
import core.message.GitDiffMessage;
import core.task.ExecutionStatus;
import core.task.Task;
import core.task.TaskPurpose;
import core.telemetry.Telemetry;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.compiler.CompilerPaths.getModuleOutputPath;

public class CompileWithJavac implements Task<GitDiffMessage, CompileWithJavacMessage> {

    private final Project project;

    public CompileWithJavac(Project project) {
        this.project = project;
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
        List<File> projectClasspath = new ArrayList<>();
        telemetry.message("Project modules:");

        for (Module module : modules) {
            List<File> moduleClasspath = getModuleClasspath(module, false);
            projectClasspath.addAll(moduleClasspath);
            telemetry.message("- %s: %d classpath items", module.getName(), moduleClasspath.size());
        }

        telemetry.message("Total project classpath: %d items", projectClasspath.size());
        return new CompileWithJavacMessage(ExecutionStatus.SUCCESS, null);
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
                    System.out.print(path);
                }
            }
        }

        return fileList;
    }
}
