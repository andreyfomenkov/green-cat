package core.settings;

public class ProjectSettings {

    public final String name;
    public final String rootDir;
    public final String buildDir;
    public final String objDir;
    public final String dexDir;

    public ProjectSettings(String name, String rootDir, String buildDir, String objDir, String dexDir) {
        this.name = name;
        this.rootDir = rootDir;
        this.buildDir = buildDir;
        this.objDir = objDir;
        this.dexDir = dexDir;
    }
}
