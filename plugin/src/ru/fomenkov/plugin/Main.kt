package ru.fomenkov.plugin

import ru.fomenkov.plugin.task.Result
import ru.fomenkov.plugin.task.config.PluginConfiguration
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.task.resolve.ProjectResolverInput
import ru.fomenkov.plugin.task.resolve.ProjectResolverOutput
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.formatMillis
import ru.fomenkov.plugin.util.timeMillis

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"
private const val PLUGIN_VERSION = "1.0"

fun main(args: Array<String>) = try {
    Telemetry.log("Starting GreenCat v$PLUGIN_VERSION...\n")
    Telemetry.isVerbose = false
    val msec = timeMillis {
        launch(
        )
    }
    Telemetry.log("Execution complete in ${formatMillis(msec)}")

} catch (error: Throwable) {
    Telemetry.err("Error (${error.message})")
    error.printStackTrace(System.err)
}

private fun launch(
    androidSdkPath: String,
    srcFiles: Set<String>,
) {
    val compilationInfo = resolveProjectCompilationInfo(
        PluginConfiguration(
            androidSdkPath = androidSdkPath,
            srcFiles = srcFiles,
        )
    )
    compileSourceFiles(compilationInfo)
}

private fun resolveProjectCompilationInfo(configuration: PluginConfiguration) = ProjectResolveTask(
    ProjectResolverInput(
        propertiesFileName = GRADLE_PROPERTIES_FILE_NAME,
        settingsFileName = GRADLE_SETTINGS_FILE_NAME,
        sourceFiles = configuration.srcFiles,
        androidSdkPath = configuration.androidSdkPath,
    )
)
    .run()
    .checkIsOk("Failed resolve project dependency graph")

private fun compileSourceFiles(compilationInfo: ProjectResolverOutput) {
    Telemetry.log("Compiling with javac...")
    exec("rm -rf ~/tmp && mkdir ~/tmp")

    compilationInfo.sourceFilesClasspath.forEach { (srcFile, classpath) ->
        val cp = classpath.joinToString(separator = ":")
        val lines = exec("javac -cp $cp -d ~/tmp $srcFile")
        val hasError = lines.find { line -> line.contains("error: ") } != null

        if (hasError) {
            lines.forEach { line -> Telemetry.err("[JAVAC] $line") }
        } else {
            Telemetry.log("[JAVAC] Build successful")
        }
    }
    exec("rm -rf ~/tmp")
}

private fun <T> Result<T>.checkIsOk(errorMessage: String) = when (this) {
    is Result.Complete -> output
    is Result.Error -> error("$errorMessage. ${error.message}")
}