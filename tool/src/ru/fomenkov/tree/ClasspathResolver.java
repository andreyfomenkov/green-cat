package ru.fomenkov.tree;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ClasspathResolver {

    private static final String PROJECT_DIR = "/Users/andrey.fomenkov/Workspace/ok";
    private static final String SETTINGS_GRADLE = "settings.gradle";
    private static final String BUILD_GRADLE = "build.gradle";

    public static void main(String[] args) throws Throwable {
        String cp = FileUtils.readFileToString(new File("/Users/andrey.fomenkov/Workspace/scripts/classpath"));
        String[] parts = cp.split(":");
        List<String> list = new ArrayList<>(parts.length);

        for (String p : parts) {
            if (p.contains("Workspace/ok/")) {
                list.add("Workspace/ok: " + p);
            } else if (p.contains("/.gradle/")) {
                list.add(".gradle: " + p);
            } else if (p.contains("/Android/sdk/")) {
                list.add("Android/sdk: " + p);
            } else if (p.contains("/JavaVirtualMachines/")) {
                list.add("JavaVirtualMachines: " + p);
            } else {
                throw new RuntimeException("WTF? " + p);
            }
        }
        Collections.sort(list);

        for (String p : list) {
            log(p);
        }
//        long startTime = System.currentTimeMillis();
//        Map<String, List<Dependency>> data = getModuleDependencies();
//
//        for (Map.Entry<String, List<Dependency>> entry : data.entrySet()) {
//            String module = entry.getKey();
//            List<Dependency> deps = entry.getValue();
//            log("MODULE: %s", module);
//
//            for (Dependency dep : deps) {
//                log(" [%s] %s", dep.isApi ? "A" : "I", dep.module);
//                List<Dependency> ds = data.get(dep.module);
//
//                if (ds == null) {
//                    ds = data.get("publish/commons/" + dep.module);
//                    if (ds == null) {
//                        throw new RuntimeException("Module not found: " + dep.module);
//                    }
//                }
//                for (Dependency d : ds) {
//                    log("   [%s] %s", d.isApi ? "A" : "I", d.module);
//                }
//            }
//            log("");
//        }
//        long endTime = System.currentTimeMillis();
//        log("Time spent: %d msec", endTime - startTime);
    }

    private static Map<String, List<Dependency>> getModuleDependencies() throws Throwable {
        Set<String> modulePaths = getModulePaths();
        Map<String, List<Dependency>> deps = new HashMap<>();

        for (String path : modulePaths) {
            File file = new File(PROJECT_DIR + "/" + path + "/" + BUILD_GRADLE);
            List<String> lines = FileUtils.readLines(file);
            deps.put(path, new ArrayList<>());

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("implementation project")) {
                    List<Dependency> list = deps.get(path);
                    trimmed = parseDependency(trimmed);
                    list.add(new Dependency(trimmed, false));
                    deps.put(path, list);

                } else if (trimmed.startsWith("api project")) {
                    List<Dependency> list = deps.get(path);
                    trimmed = parseDependency(trimmed);
                    list.add(new Dependency(trimmed, true));
                    deps.put(path, list);
                }
            }
        }
        return deps;
    }

    private static String parseDependency(String line) {
        int startIndex = line.indexOf(":");
        int endIndex = line.indexOf("')");

        if (endIndex == -1) {
            endIndex = line.indexOf("\")");
        }
        if (startIndex == -1 || endIndex == -1) {
            throw new IllegalArgumentException("Failed to parse dependency: " + line);
        }
        return line.substring(startIndex + 1, endIndex);
    }

    private static Set<String> getModulePaths() throws IOException {
        File file = new File(PROJECT_DIR + "/" + SETTINGS_GRADLE);
        List<String> lines = FileUtils.readLines(file);
        Set<String> names = new HashSet<>();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("':")) {
                int startIndex = trimmed.indexOf(":") + 1;
                int endIndex = trimmed.indexOf("'", 1);
                names.add(
                        trimmed.substring(startIndex, endIndex)
                                .replace(":", "/")
                );
            } else if (trimmed.startsWith("'[")) {
                int startIndex = trimmed.indexOf("[") + 1;
                int endIndex = trimmed.indexOf("'", 1);
                names.add(
                        trimmed.substring(startIndex, endIndex)
                                .replace("]:", "/")
                );
            }
        }
        return names;
    }

    private static void log(String format, Object... args) {
        System.out.printf((format) + "%n", args);
    }

    private static class Dependency {

        public final String module;
        public final boolean isApi;

        public Dependency(String module, boolean isApi) {
            this.module = module;
            this.isApi = isApi;
        }
    }
}
