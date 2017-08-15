package ru.fomenkov.input;

public class LibraryInput {

    private String projectPath;
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

    @Override
    public String toString() {
        return String.format("PROJECT PATH: %s, ANDROID SDK PATH: %s, CLASSPATH: %s", projectPath, androidSdkPath, classpath);
    }
}
