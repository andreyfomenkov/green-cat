package core;

import java.io.File;

public class GreenCat {

    public static final String PLUGIN_NAME = "GreenCat";
    public static final String EVENT_LOG_GROUP_ID = "GreenCat";
    private static final String BUILD_PATH = "/build/greencat";
    private static final String COMPILE_PATH = BUILD_PATH + "/compile";
    private static final String LAMBDA_PATH = BUILD_PATH + "/lambda";
    private static final String DEX_BUILD_PATH = BUILD_PATH + "/dex";
    private static final String DEX_DEPLOY_PATH = "/sdcard/greencat";

    public static File getBuildDir(String projectDir) {
        return new File(projectDir + BUILD_PATH);
    }

    public static File getCompileDir(String projectDir) {
        return new File(projectDir + COMPILE_PATH);
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
