package core;

import java.io.File;

public class GreenCat {

    public static final String PLUGIN_NAME = "GreenCat";
    public static final String EVENT_LOG_GROUP_ID = "GreenCat";
    private static final String BUILD_PATH = "/build/greencat";
    private static final String OBJ_PATH = BUILD_PATH + "/obj";
    private static final String RETROLAMBDA_PATH = BUILD_PATH + "/lambda";
    private static final String DESUGAR_PATH = BUILD_PATH + "/desugar";
    private static final String KOTLIN_PATH = BUILD_PATH + "/kotlin";
    private static final String DEX_PATH = BUILD_PATH + "/dex";
    private static final String CLASSPATH_PATH = BUILD_PATH + "/classpath.txt";

    public static File getBuildPath(String projectDir) {
        return new File(projectDir + BUILD_PATH);
    }

    public static File getObjPath(String projectDir) {
        return new File(projectDir + OBJ_PATH);
    }

    public static File getRetrolambdaPath(String projectDir) {
        return new File(projectDir + RETROLAMBDA_PATH);
    }

    public static File getDexPath(String projectDir) {
        return new File(projectDir + DEX_PATH);
    }

    public static File getClasspathDumpFilePath(String projectDir) {
        return new File(projectDir + CLASSPATH_PATH);
    }
}
