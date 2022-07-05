package ru.fomenkov.plugin

import ru.fomenkov.plugin.params.Param
import ru.fomenkov.plugin.params.PluginParams
import ru.fomenkov.plugin.params.PluginParamsReader
import ru.fomenkov.plugin.repository.JetifiedJarRepository
import ru.fomenkov.plugin.repository.MetadataArtifactDependencyResolver
import ru.fomenkov.plugin.repository.parser.JetifiedResourceParser
import ru.fomenkov.plugin.repository.parser.MetadataDescriptionParser
import ru.fomenkov.plugin.task.compile.CompileTask
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.task.resolve.ProjectResolverInput
import ru.fomenkov.plugin.task.resolve.ProjectResolverOutput
import ru.fomenkov.plugin.util.Telemetry
import java.util.concurrent.Executors

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"
private const val PLUGIN_VERSION = "2.0"
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
    val params = readParams(args) ?: throw IllegalArgumentException("No arguments provided")
    val projectInfo = resolveProject(params)
    compile(params, projectInfo)
    Telemetry.log("DEX file successfully generated")
}

private fun resolveProject(params: PluginParams): ProjectResolverOutput {
    val input = ProjectResolverInput(
        propertiesFileName = GRADLE_PROPERTIES_FILE_NAME,
        settingsFileName = GRADLE_SETTINGS_FILE_NAME,
        androidSdkPath = params.androidSdkRoot,
        greencatRoot = params.greencatRoot,
        mappedModules = params.mappedModules,
    )
    val jetifiedResourceParser = JetifiedResourceParser()
    val jetifiedJarRepository = JetifiedJarRepository(jetifiedResourceParser)
    val metadataDescriptionParser = MetadataDescriptionParser()
    val artifactResolver = MetadataArtifactDependencyResolver(jetifiedJarRepository, metadataDescriptionParser)
    return ProjectResolveTask(input, artifactResolver).run()
}

private fun compile(params: PluginParams, projectInfo: ProjectResolverOutput) {
    CompileTask(
        greencatRoot = params.greencatRoot,
        androidSdkRoot = params.androidSdkRoot,
        deviceApiLevel = params.deviceApiLevel,
        projectInfo = projectInfo,
        executor = executor,
    ).run()
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