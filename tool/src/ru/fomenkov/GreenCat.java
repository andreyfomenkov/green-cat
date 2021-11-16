package ru.fomenkov;

import java.io.File;

public class GreenCat {

    public static final String LAUNCHER_FILE = "greencat.launcher";
    public static final String VERSION = "1.0";
    private static final String BUILD_PATH = "/build/greencat";
    private static final String DEX_BUILD_PATH = BUILD_PATH + "/dex";
    private static final String DEX_DEPLOY_PATH = "/sdcard/greencat";

    public static File getBuildDir(String projectDir) {
        return new File(projectDir + BUILD_PATH);
    }

    public static File getCompileDir(String projectDir, String moduleName) {
        return new File(projectDir + BUILD_PATH + "/compile/" + moduleName);
    }

    public static File getDexBuildDir(String projectDir) {
        return new File(projectDir + DEX_BUILD_PATH);
    }

    public static String getDexDeployPath() {
        return DEX_DEPLOY_PATH;
    }
}
