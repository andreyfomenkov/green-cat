package ru.fomenkov.task;

public enum TaskPurpose {
    SETUP_PROJECT("Setting up build paths"),
    RESOLVE_MODULES("Resolving project modules"),
    GIT_DIFF("Determining source changes"),
    COMPILE_WITH_JAVAC("Compiling with javac"),
    ANNOTATION_PROCESSING("Annotation processing"),
    COMPILE_WITH_KOTLIN("Compiling with Kotlin"),
    PROCESS_WITH_RETROLAMBDA("Processing with Retrolambda"),
    PROCESS_WITH_DESUGAR("Processing with Desugar"),
    MAKE_DEX("Dexing"),
    DEPLOY_ON_DEVICE("Deploying changes on Android device"),
    RESTART_APPLICATION("Restarting application");

    private final String purpose;

    TaskPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String value() {
        return purpose;
    }
}
