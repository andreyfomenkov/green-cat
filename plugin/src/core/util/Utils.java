package core.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.sun.istack.internal.Nullable;
import core.command.CommandExecutor;
import core.command.CommandLineBuilder;
import core.command.Parameter;

import java.io.File;
import java.util.List;

public class Utils {

    public static String formatNanoTimeToSeconds(long nanoTime) {
        String format = String.format("%.1f", nanoTime / Math.pow(10, 9));
        return format.endsWith(".0") ? format.substring(0, 1) : format;
    }

    public static List<String> findFilesRecursive(String path, String pattern) {
        String cmd = CommandLineBuilder.create("find")
                .add(new Parameter(path))
                .add(new Parameter("-name", pattern))
                .build();
        return CommandExecutor.exec(cmd);
    }

    @Nullable
    public static String getAndroidSdkPath(Project project) {
        ModuleManager manager = ModuleManager.getInstance(project);
        String androidSdkPath = null;

        for (Module module : manager.getModules()) {
            if (androidSdkPath != null) {
                break;
            }
            Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

            if (sdk != null && sdk.getHomePath() != null) {
                File file = new File(sdk.getHomePath());
                String[] contents = file.list();

                if (contents != null) {
                    for (String path : contents) {
                        if (path.equals("build-tools")) {
                            androidSdkPath = sdk.getHomePath();
                            break;
                        }
                    }
                }
            }
        }
        return androidSdkPath;
    }
}
