package ru.fomenkov.input;

public class LibraryInput {

    private String projectPath;
    private String[] projectModules;
    private String classpath;
    private String androidSdkPath;

    public String getAndroidSdkPath() {
        return androidSdkPath;
    }

    public void setAndroidSdkPath(String androidSdkPath) {
        this.androidSdkPath = androidSdkPath;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getClasspath() {
        return classpath;
    }

    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    public void setProjectModules(String[] modules) {
        this.projectModules = modules;
    }

    public String[] getProjectModules() {
        return projectModules;
    }
}
