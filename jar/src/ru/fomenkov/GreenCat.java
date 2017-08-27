package ru.fomenkov;

import java.io.File;

public class GreenCat {

    public static final String PROPERTIES_FILE = "greencat.properties";
    public static final String LAUNCHER_FILE = "greencat.launcher";
    private static final String BUILD_PATH = "/build/greencat";
    private static final String LAMBDA_PATH = BUILD_PATH + "/lambda";
    private static final String DEX_BUILD_PATH = BUILD_PATH + "/dex";
    private static final String DEX_DEPLOY_PATH = "/sdcard/greencat";

    public static File getBuildDir(String projectDir) {
        return new File(projectDir + BUILD_PATH);
    }

    public static File getCompileDir(String projectDir, String moduleName) {
        return new File(projectDir + BUILD_PATH + "/compile/" + moduleName);
    }

    public static File getLambdaDir(String projectDir) {
        return new File(projectDir + LAMBDA_PATH);
    }

    public static File getDexBuildDir(String projectDir) {
        return new File(projectDir + DEX_BUILD_PATH);
    }

    public static String getDexDeployPath() {
        return DEX_DEPLOY_PATH;
    }
}
