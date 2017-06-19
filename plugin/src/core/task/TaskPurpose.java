package core.task;

public enum TaskPurpose {
    GIT_DIFF("Determining source changes"),
    CLEANUP_BUILD("Cleaning up build folder"),
    COMPILE_WITH_JAVAC("Compiling with javac"),
    COMPILE_WITH_KOTLIN("Compiling with Kotlin"),
    PROCESS_WITH_RETROLAMBDA("Processing with Retrolambda"),
    PROCESS_WITH_DESUGAR("Processing with Desugar"),
    MAKE_DEX("Dexing"),
    PUSH_DEX_TO_DEVICE("Pushing to device"),
    RESTART_APPLICATION("Restarting application");

    private final String purpose;

    TaskPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String value() {
        return purpose;
    }
}
