package ru.fomenkov.plugin

import ru.fomenkov.plugin.params.Param
import ru.fomenkov.plugin.params.PluginParamsReader
import ru.fomenkov.plugin.repository.ClassFileRepository
import ru.fomenkov.plugin.repository.JetifiedJarRepository
import ru.fomenkov.plugin.repository.SupportJarRepository
import ru.fomenkov.plugin.task.compile.CompileTask
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.task.resolve.ProjectResolverInput
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.formatMillis
import ru.fomenkov.plugin.util.timeMillis
import java.util.concurrent.Executors

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"
private const val PLUGIN_VERSION = "1.6"
private val cpuCount = Runtime.getRuntime().availableProcessors()
private val executor = Executors.newFixedThreadPool(cpuCount)

fun main(args: Array<String>) = try {
    Telemetry.isVerbose = false
//    launch(args)
    //
    val classFileRepo = ClassFileRepository()
    val jetifiedJarRepo = JetifiedJarRepository()
    val supportJarRepo = SupportJarRepository()

    val scanTime = timeMillis {
        classFileRepo.scan()
        jetifiedJarRepo.scan()
        supportJarRepo.scan()
    }
    val findTime = timeMillis {
        val packageName = "ru.ok.android.rxbillingmanager.model" // TODO: static imports
        val classFileResource = classFileRepo.find(packageName)

        if (classFileResource != null) {
            Telemetry.log("$packageName found in class file repository: $classFileResource")
        } else {
            val jetifiedJarResource = jetifiedJarRepo.find(packageName)

            if (jetifiedJarResource != null) {
                Telemetry.log("$packageName found in jetified JAR repository: $jetifiedJarResource")
            } else {
                val supportJarResource = supportJarRepo.find(packageName)

                if (supportJarResource != null) {
                    Telemetry.log("$packageName found in support JAR repository: $supportJarResource")
                } else {
                    Telemetry.err("$packageName not found. Traversing subtree")

                    (classFileRepo.subtree(packageName) + jetifiedJarRepo.subtree(packageName) + supportJarRepo.subtree(packageName))
                        .forEach { Telemetry.log("[RES] $it") }
                }
            }
        }
    }
    Telemetry.log("\nScan time total: ${formatMillis(scanTime)}, find time: ${formatMillis(findTime)}")
    //

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