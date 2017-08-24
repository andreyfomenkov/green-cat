package ui.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;

import java.io.File;

public class Utils {

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
