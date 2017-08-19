package ru.fomenkov.task.resolve;

import com.sun.istack.internal.Nullable;
import ru.fomenkov.Module;
import ru.fomenkov.command.CommandExecutor;
import ru.fomenkov.command.CommandLineBuilder;
import ru.fomenkov.command.Parameter;
import ru.fomenkov.exception.ModuleFileParsingException;
import ru.fomenkov.message.ModulesResolveMessage;
import ru.fomenkov.message.ProjectSetupMessage;
import ru.fomenkov.task.ExecutionStatus;
import ru.fomenkov.task.Task;
import ru.fomenkov.task.TaskPurpose;
import ru.fomenkov.telemetry.Telemetry;
import ru.fomenkov.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModulesResolveTask implements Task<ProjectSetupMessage, ModulesResolveMessage> {

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.RESOLVE_MODULES;
    }

    @Override
    public ModulesResolveMessage exec(Telemetry telemetry, ProjectSetupMessage message) {
        if (message.status == ExecutionStatus.ERROR) {
            return new ModulesResolveMessage(ExecutionStatus.ERROR, "Previous task failed");
        }

        String projectPath = message.getProjectPath();
        String cmd = CommandLineBuilder.create("find")
                .add(new Parameter(projectPath, "-name '*.iml'"))
                .build();

        List<String> output = CommandExecutor.execOnInputStream(cmd);
        List<Module> modules = new ArrayList<>();

        for (String path : output) {
            path = path.trim();

            if (path.endsWith(".iml")) {
                try {
                    Module module = getModuleInfo(path);

                    if (module != null) {
                        modules.add(module);
                    }
                } catch (ModuleFileParsingException e) {
                    telemetry.error("Failed to parse project module file %s: %s", path, e.getMessage());
                    return new ModulesResolveMessage(ExecutionStatus.ERROR, "Error parsing project module .iml file");
                }
            }
        }

        int modulesCount = modules.size();
        telemetry.message("Found %d module%s", modulesCount, modulesCount == 1 ? "" : "s:");

        for (Module module : modules) {
            String modulePath = module.modulePath.replace(projectPath, "");

            if (module.buildPath == null) {
                telemetry.warn("- [%s : %s] PATH: %s, OUTPUT: %s", module.name, module.variant, modulePath, "* NO BUILD PATH*");
            } else {
                String buildPath = module.buildPath.replace(projectPath, "...");
                telemetry.message("- [%s : %s] PATH: %s, OUTPUT: %s", module.name, module.variant, modulePath, buildPath);
            }
        }

        return new ModulesResolveMessage(projectPath, modules);
    }

    @Nullable
    private Module getModuleInfo(String moduleFilePath) throws ModuleFileParsingException {
        String moduleName = new File(moduleFilePath).getName().split("\\.")[0];
        String cmd = CommandLineBuilder.create("cat")
                .add(new Parameter(moduleFilePath))
                .add(new Parameter("| grep 'output url='"))
                .build();
        List<String> output = CommandExecutor.execOnInputStream(cmd);

        if (output.size() == 0) {
            return null;

        } else if (output.size() > 1) {
            throw new ModuleFileParsingException("Multiple XML entities <output url= ... /> found");

        } else {
            int pathLength = moduleFilePath.lastIndexOf('/');
            String modulePath = moduleFilePath.substring(0, pathLength);

            int variantStartIndex = output.get(0).lastIndexOf("/classes/") + 9;
            int variantEndIndex = output.get(0).lastIndexOf("\"");
            String moduleVariant = output.get(0).substring(variantStartIndex, variantEndIndex);

            String buildPath = modulePath + "/build/intermediates/classes/" + moduleVariant;

            if (!Utils.fileExists(buildPath)) {
                buildPath = modulePath + "/build/classes/" + moduleVariant;
            }

            if (!Utils.fileExists(buildPath)) {
                buildPath = null;
            }

            return new Module(moduleName, modulePath, buildPath, moduleVariant);
        }
    }
}
