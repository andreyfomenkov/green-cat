package ru.fomenkov.runner.params

data class RunnerParams(
    val sshHost: String,
    val projectRoot: String,
    val androidSdkRoot: String,
    val greencatRoot: String,
    val mode: RunnerMode,
    val modulesMap: Map<String, String>,
)

sealed class RunnerMode {

    data class Debug(
        val componentName: String,
    ) : RunnerMode()

    data class UiTest(
        val testClass: String,
        val testRunner: String,
    ) : RunnerMode()

    object Update : RunnerMode()
}

enum class Param(
    val key: String,
    val description: String,
) {
    SSH_HOST(key = "-h", description = "Remote SSH hostname or alias"),
    PROJECT_ROOT(key = "-p", description = "Android project root directory on the remote host"),
    ANDROID_SDK_ROOT(key = "-s", description = "Android SDK root directory on the remote host"),
    GREENCAT_ROOT(key = "-g", description = "GreenCat root directory on the remote host"),
    RUNNER_MODE(key = "-m", description = "Runner mode (debug or uitest)"),
    COMPONENT_NAME(key = "-c", description = "Component name for main activity in 'debug' mode"),
    TEST_CLASS(key = "-t", description = "Espresso test canonical class name in 'uitest' mode"),
    TEST_RUNNER(key = "-r", description = "Espresso test runner in 'uitest' mode"),
    MAPPED_MODULES(key = "-a", description = "Mapped modules, ':' splitted, comma separated"),
    UPDATE(key = "-u", description = "Check for update")
}

enum class Mode(val mode: String) {
    DEBUG("debug"),
    UITEST("uitest")
}