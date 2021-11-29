package ru.fomenkov.task.resolve;

import com.sun.istack.internal.Nullable;

import org.apache.commons.io.FileUtils;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ModulesResolveTask implements Task<ProjectSetupMessage, ModulesResolveMessage> {

    @Override
    public TaskPurpose getPurpose() {
        return TaskPurpose.RESOLVE_MODULES;
    }

    @Override
    public ModulesResolveMessage exec(ProjectSetupMessage message) {
        if (message.status == ExecutionStatus.ERROR) {
            return new ModulesResolveMessage(ExecutionStatus.ERROR, "Previous task failed");
        }
        String projectPath = message.getProjectPath();
        List<Module> modules = new ArrayList<>();
        File file = new File("modules");

        if (file.exists()) {
            // TODO: error handling
            try {
                List<String> encoded = FileUtils.readLines(file);

                for (String entry : encoded) {
                    Module module = Module.decode(entry);
                    modules.add(module);
                }
                Telemetry.log("Cached modules file found with %s entries", modules.size());

            } catch (IOException e) {
                Telemetry.err("Failed to parse modules file: %s", e.getMessage());
            }
        } else {
            String cmd = CommandLineBuilder.create("find")
                    .add(new Parameter(projectPath + "/.idea/modules", "-name '*.iml'"))
                    .build();
            List<String> output = CommandExecutor.execOnInputStream(cmd);

            for (String path : output) {
                path = path.trim();

                if (path.endsWith(".iml")) {
                    try {
                        Module module = getModuleInfo(path);

                        if (module != null) {
                            modules.add(module);
                        }
                    } catch (ModuleFileParsingException e) {
                        Telemetry.err("Failed to parse project module file %s: %s", path, e.getMessage());
                        return new ModulesResolveMessage(ExecutionStatus.ERROR, "Error parsing project module .iml file");
                    }
                }
            }
            List<String> encoded = new ArrayList<>(modules.size());

            for (Module module : modules) {
                encoded.add(module.encode());
            }
            try {
                FileUtils.writeLines(file, encoded);
            } catch (IOException e) {
                Telemetry.err("Failed to write modules file: %s", e.getMessage());
            }
        }
        int modulesCount = modules.size();
        Telemetry.log("Found %d module%s", modulesCount, modulesCount == 1 ? "" : "s:");

        // TODO: what if no build path for module?
//        for (Module module : modules) {
//            String modulePath = module.modulePath.replace(projectPath, "");
//
//            if (module.buildPath == null) {
//                Telemetry.err("- [%s : %s] PATH: %s, OUTPUT: %s", module.name, module.variant, modulePath, "* NO BUILD PATH*");
//            } else {
//                String buildPath = module.buildPath.replace(projectPath, "...");
//                Telemetry.log("- [%s : %s] PATH: %s, OUTPUT: %s", module.name, module.variant, modulePath, buildPath);
//            }
//        }
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
//            int pathLength = moduleFilePath.lastIndexOf('/');
//            String modulePath = moduleFilePath.substring(0, pathLength);
//
//            int variantStartIndex = output.get(0).lastIndexOf("/classes") + 8;
//            int variantEndIndex = output.get(0).lastIndexOf("\"");
//            String moduleVariant = output.get(0).substring(variantStartIndex, variantEndIndex);
//            String buildPath = modulePath + "/build/intermediates/classes/" + moduleVariant;

            String moduleFileDir = moduleFilePath.substring(0, moduleFilePath.lastIndexOf("/"));
            String pathPlaceholder = output.get(0).substring(output.get(0).indexOf("//") + 2, output.get(0).lastIndexOf("/classes") + 8);
            String buildPath = pathPlaceholder.replace("$MODULE_DIR$", moduleFileDir);

            String left = buildPath.substring(0, buildPath.indexOf(".idea"));
            String right = buildPath.substring(buildPath.lastIndexOf("../") + 3);
            buildPath = left + right;

            String moduleVariant = "debug";
            String modulePath = buildPath.substring(0, buildPath.lastIndexOf("/build/"));

//            if (!Utils.fileExists(buildPath)) {
//                buildPath = modulePath + "/build/classes/" + moduleVariant;
//            }

            if (!Utils.fileExists(buildPath)) {
                buildPath = null;
            }
            return new Module(moduleName, modulePath, buildPath, moduleVariant);
        }
    }
}
