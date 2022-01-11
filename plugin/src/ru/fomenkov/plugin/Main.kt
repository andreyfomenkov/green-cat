package ru.fomenkov.plugin

import ru.fomenkov.plugin.task.Result
import ru.fomenkov.plugin.task.config.PluginConfiguration
import ru.fomenkov.plugin.task.config.ReadPluginConfigTask
import ru.fomenkov.plugin.task.resolve.GradleProjectInput
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.util.Telemetry

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"

fun main(args: Array<String>) {
    Telemetry.isVerbose = false
    val configuration = readPluginConfiguration()
    val graph = resolveProjectGraph(configuration)

    Telemetry.log("\nComplete in ")
}

private fun readPluginConfiguration() = ReadPluginConfigTask(
    configFilePath = "../greencat.properties", // TODO: get from argument
).run().let { result ->
    when (result) {
        is Result.Complete -> result.output
        is Result.Error -> error("Failed to read plugin configuration: ${result.error.message}")
    }
}

private fun resolveProjectGraph(configuration: PluginConfiguration) = ProjectResolveTask(
    GradleProjectInput(
        propertiesFileName = GRADLE_PROPERTIES_FILE_NAME,
        settingsFileName = GRADLE_SETTINGS_FILE_NAME,
        ignoredModules = configuration.ignoredModules,
        ignoredLibs = configuration.ignoredLibs,
    )
).run().let { result ->
    when (result) {
        is Result.Complete -> result.output
        is Result.Error -> error("Failed to read plugin configuration: ${result.error.message}")
    }
}