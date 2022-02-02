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
import java.io.File
import java.io.FileFilter

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"
private const val PLUGIN_VERSION = "1.0"

fun main(args: Array<String>) = try {
    Telemetry.log("Starting GreenCat v$PLUGIN_VERSION...\n")
    Telemetry.isVerbose = false
    val msec = timeMillis {
        launch(
            androidSdkPath = args[0],  // TODO: for debug
            srcFiles = setOf(args[1]), // TODO: for debug
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
    compileAndDexSourceFiles(compilationInfo, androidSdkPath)
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

private fun compileAndDexSourceFiles(compilationInfo: ProjectResolverOutput, androidSdkPath: String) {
    Telemetry.log("Compiling with javac...")
    exec("rm -rf ~/greencat/tmp && mkdir ~/greencat/tmp")
    exec("rm -rf ~/greencat/dex && mkdir ~/greencat/dex")

    compilationInfo.sourceFilesClasspath.forEach { (srcFile, classpath) ->
        val cp = classpath.joinToString(separator = ":")

        Telemetry.log("CLASSPATH ${cp.length / 1000}k symbols")

        val lines = exec("javac -cp $cp -d ~/greencat/tmp $srcFile")
        val hasError = lines.find { line -> line.contains("error: ") } != null

        if (hasError) {
            lines.forEach { line -> Telemetry.err("[JAVAC] $line") }
        } else {
            lines.forEach { line -> Telemetry.log("[JAVAC] $line") }
            Telemetry.log("[JAVAC] Build successful")
        }
    }
    Telemetry.log("\nListing compiled .class files:")
    val classFilePaths = exec("find ~/greencat/tmp -name '*.class'", print = true)

    Telemetry.log("\nLooking for D8")
    val d8ToolPath = File("$androidSdkPath/build-tools").run {
        if (!exists()) {
            error("No build-tools directory in Android SDK: $absolutePath")
        }
        val dirs = listFiles { file -> file.isDirectory }

        if (dirs.isNullOrEmpty()) {
            error("No build tools installed")
        }
        val buildToolsDir = dirs.sortedDescending()[0]
        val d8ToolPath = "${buildToolsDir.absolutePath}/d8"

        if (File(d8ToolPath).exists()) {
            Telemetry.log("Using $d8ToolPath")
        } else {
            error("No D8 tool found in ${buildToolsDir.absolutePath}")
        }
        d8ToolPath
    }
    Telemetry.log("\nDexing...")
    Telemetry.log("Found ${classFilePaths.size} class(es)")

    // See https://stackoverflow.com/questions/30081386/how-to-put-specific-classes-into-main-dex-file!
    val cmd = "$d8ToolPath ${classFilePaths.filterNot { it.contains("$") }.joinToString(separator = " ")} --output ~/greencat/dex"
    Telemetry.log("CMD: $cmd")
    exec(cmd).forEach { line -> Telemetry.log("[D8] $line") }

    exec("echo; ls -lh ~/greencat/dex", print = true)
}

private fun <T> Result<T>.checkIsOk(errorMessage: String) = when (this) {
    is Result.Complete -> output
    is Result.Error -> error("$errorMessage. ${error.message}")
}