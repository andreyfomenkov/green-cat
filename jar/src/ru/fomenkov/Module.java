package ru.fomenkov;

public class Module {

    public final String name;
    public final String modulePath;
    public final String buildPath;
    public final String variant;

    public Module(String name, String modulePath, String buildPath, String variant) {
        this.name = name;
        this.modulePath = modulePath;
        this.buildPath = buildPath;
        this.variant = variant;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Module) {
            Module module = (Module) obj;
            return module.name.equals(name);
        }
        return false;
    }
}
