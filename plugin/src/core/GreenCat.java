package core;

import java.io.File;

public class GreenCat {

    public static final String PLUGIN_NAME = "GreenCat";
    public static final String EVENT_LOG_GROUP_ID = "GreenCat";
    private static final String BUILD_PATH = "/build/greencat";
    private static final String OBJ_PATH = BUILD_PATH + "/obj";
    private static final String DESUGAR_PATH = BUILD_PATH + "/desugar";
    private static final String KOTLIN_PATH = BUILD_PATH + "/kotlin";
    private static final String DEX_BUILD_PATH = BUILD_PATH + "/dex";
    private static final String DEX_DEPLOY_PATH = "/sdcard/greencat";

    public static File getBuildDir(String projectDir) {
        return new File(projectDir + BUILD_PATH);
    }

    public static File getObjDir(String projectDir) {
        return new File(projectDir + OBJ_PATH);
    }

    public static File getDexBuildDir(String projectDir) {
        return new File(projectDir + DEX_BUILD_PATH);
    }

    public static String getDexDeployPath() {
        return DEX_DEPLOY_PATH;
    }
}
