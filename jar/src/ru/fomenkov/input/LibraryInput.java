package ru.fomenkov.input;

public class LibraryInput {

    private String projectDir;
    private String classpath;

    public String getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(String projectDir) {
        this.projectDir = projectDir;
    }

    public String getClasspath() {
        return classpath;
    }

    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    @Override
    public String toString() {
        return String.format("PROJECT DIR: %s, CLASSPATH: %s", projectDir, classpath);
    }
}
