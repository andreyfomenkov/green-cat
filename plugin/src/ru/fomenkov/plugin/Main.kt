package ru.fomenkov.plugin

import ru.fomenkov.plugin.params.Param
import ru.fomenkov.plugin.params.PluginParamsReader
import ru.fomenkov.plugin.task.compile.CompileTask
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.task.resolve.ProjectResolverInput
import ru.fomenkov.plugin.util.Telemetry
import java.util.concurrent.Executors

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"
private const val PLUGIN_VERSION = "1.2"
private val cpuCount = Runtime.getRuntime().availableProcessors()
private val executor = Executors.newFixedThreadPool(cpuCount)

fun main(args: Array<String>) = try {
    Telemetry.isVerbose = false
    launch(args)

} catch (error: Throwable) {
    when (error.message.isNullOrBlank()) {
        true -> Telemetry.err("Build failed")
        else -> Telemetry.err("Build failed: ${error.message}")
    }
    Telemetry.err(error.stackTraceToString())
} finally {
    executor.shutdown()
}

private fun launch(args: Array<String>) {
    val params = readParams(args) ?: return
    val projectResolverInput = ProjectResolverInput(
        propertiesFileName = GRADLE_PROPERTIES_FILE_NAME,
        settingsFileName = GRADLE_SETTINGS_FILE_NAME,
        androidSdkPath = params.androidSdkRoot,
        greencatRoot = params.greencatRoot,
        mappedModules = params.mappedModules,
    )
    val projectInfo = ProjectResolveTask(projectResolverInput, executor).run()
    CompileTask(
        greencatRoot = params.greencatRoot,
        androidSdkRoot = params.androidSdkRoot,
        projectInfo = projectInfo,
        executor = executor,
    ).run()
    Telemetry.log("DEX file successfully generated")
}

private fun readParams(args: Array<String>) = when {
    args.isEmpty() -> error("No parameters specified")
    args.size == 1 -> {
        val param = args.first()

        if (param == Param.VERSION.key) {
            Telemetry.log(PLUGIN_VERSION)
            null
        } else {
            error("Unknown single parameter: $param")
        }
    }
    else -> PluginParamsReader(args).read()
}