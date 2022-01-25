package ru.fomenkov.plugin

import ru.fomenkov.plugin.task.Result
import ru.fomenkov.plugin.task.config.PluginConfiguration
import ru.fomenkov.plugin.task.config.ReadPluginConfigTask
import ru.fomenkov.plugin.task.resolve.ProjectResolverInput
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.formatMillis
import ru.fomenkov.plugin.util.timeMillis

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"
private const val PLUGIN_VERSION = "1.0"

fun main(args: Array<String>) = try {
    Telemetry.log("Starting GreenCat v$PLUGIN_VERSION...\n")
    Telemetry.isVerbose = false
    val msec = timeMillis(::launch)
    Telemetry.log("Execution complete in ${formatMillis(msec)}")

} catch (error: Throwable) {
    Telemetry.err("Error (${error.message})")
}

private fun launch() {
    val configuration = readPluginConfiguration()
    val resolverOutput = resolveProjectGraph(configuration)
}

private fun readPluginConfiguration() = ReadPluginConfigTask(
    configFilePath = "../greencat.properties", // TODO: get from argument
)
    .run()
    .checkIsOk("Failed to read plugin configuration")

private fun resolveProjectGraph(configuration: PluginConfiguration) = ProjectResolveTask(
    ProjectResolverInput(
        propertiesFileName = GRADLE_PROPERTIES_FILE_NAME,
        settingsFileName = GRADLE_SETTINGS_FILE_NAME,
        sourceFiles = setOf(configuration.src), // TODO: temporary read from config file. Remove then
        androidSdkPath = configuration.androidSdkPath,
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