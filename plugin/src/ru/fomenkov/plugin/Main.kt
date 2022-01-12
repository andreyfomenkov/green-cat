package ru.fomenkov.plugin

import ru.fomenkov.plugin.task.Result
import ru.fomenkov.plugin.task.config.PluginConfiguration
import ru.fomenkov.plugin.task.config.ReadPluginConfigTask
import ru.fomenkov.plugin.task.resolve.GradleProjectInput
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.formatMillis

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"
private const val PLUGIN_VERSION = "1.0"

fun main(args: Array<String>) = try {
    Telemetry.log("Starting GreenCat v$PLUGIN_VERSION...\n")
    Telemetry.isVerbose = true

    val startTime = System.currentTimeMillis()
    val configuration = readPluginConfiguration()
    val graph = resolveProjectGraph(configuration)
    val endTime = System.currentTimeMillis()

    Telemetry.log("\nComplete in ${formatMillis(endTime - startTime)}")
} catch (error: Throwable) {
    Telemetry.err("\nError (${error.message})")
}

private fun readPluginConfiguration() = ReadPluginConfigTask(
    configFilePath = "../greencat.properties", // TODO: get from argument
)
    .run()
    .checkIsOk("Failed to read plugin configuration")

private fun resolveProjectGraph(configuration: PluginConfiguration) = ProjectResolveTask(
    GradleProjectInput(
        propertiesFileName = GRADLE_PROPERTIES_FILE_NAME,
        settingsFileName = GRADLE_SETTINGS_FILE_NAME,
        ignoredModules = configuration.ignoredModules,
        ignoredLibs = configuration.ignoredLibs,
    )
)
    .run()
    .checkIsOk("Failed resolve project dependency graph")

private fun <T> Result<T>.checkIsOk(errorMessage: String) = when (this) {
    is Result.Complete -> output
    is Result.Error -> error("$errorMessage. ${error.message}")
}