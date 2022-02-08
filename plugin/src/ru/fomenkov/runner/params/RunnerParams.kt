package ru.fomenkov.runner.params

data class RunnerParams(
    val sshHost: String,
    val projectRoot: String,
    val androidSdkRoot: String,
    val mode: RunnerMode,
)

sealed class RunnerMode {

    data class Debug(
        val componentName: String,
    ) : RunnerMode()

    data class UiTest(
        val testClass: String,
        val testRunner: String,
    ) : RunnerMode()
}

enum class Param(
    val key: String,
    val description: String,
) {
    SSH_HOST(key = "-h", description = "Remote SSH hostname or alias"),
    PROJECT_ROOT(key = "-p", description = "Android project root on the remote host"),
    ANDROID_SDK_ROOT(key = "-s", description = "Android SDK root on the remote host"),
    RUNNER_MODE(key = "-m", description = "Runner mode (debug or uitest)"),
    COMPONENT_NAME(key = "-c", description = "Component name for main activity in 'debug' mode"),
    TEST_CLASS(key = "-t", description = "Espresso test canonical class name in 'uitest' mode"),
    TEST_RUNNER(key = "-r", description = "Espresso test runner in 'uitest' mode"),
}

enum class Mode(val mode: String) {
    DEBUG("debug"),
    UITEST("uitest")
}