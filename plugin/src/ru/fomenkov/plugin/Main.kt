package ru.fomenkov.plugin

import ru.fomenkov.plugin.params.Param
import ru.fomenkov.plugin.params.PluginParamsReader
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.task.resolve.ProjectResolverInput
import ru.fomenkov.plugin.util.Telemetry

private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
private const val GRADLE_SETTINGS_FILE_NAME = "settings.gradle"
private const val PLUGIN_VERSION = "1.0"

fun main(args: Array<String>) = try {
    Telemetry.isVerbose = false
    launch(args)

} catch (error: Throwable) {
    when (error.message.isNullOrBlank()) {
        true -> Telemetry.err("Build failed")
        else -> Telemetry.err("Build failed: ${error.message}")
    }
}

private fun launch(args: Array<String>) {
    val params = readParams(args) ?: return
    val compilationInfo = resolveProjectCompilationInfo(
        androidSdkRoot = params.androidSdkRoot,
        greencatRoot = params.greencatRoot,
        mappedModules = params.mappedModules,
    )
    Telemetry.log("Build successful")
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

private fun resolveProjectCompilationInfo(
    androidSdkRoot: String,
    greencatRoot: String,
    mappedModules: Map<String, String>,
) = ProjectResolveTask(
    ProjectResolverInput(
        propertiesFileName = GRADLE_PROPERTIES_FILE_NAME,
        settingsFileName = GRADLE_SETTINGS_FILE_NAME,
        androidSdkPath = androidSdkRoot,
        greencatRoot = greencatRoot,
        mappedModules = mappedModules,
    )
).run()

//private fun compileAndDexSourceFiles(compilationInfo: ProjectResolverOutput, androidSdkPath: String) {
//    Telemetry.log("Compiling with javac...")
//    exec("rm -rf ~/greencat/tmp && mkdir ~/greencat/tmp")
//    exec("rm -rf ~/greencat/dex && mkdir ~/greencat/dex")
//
//    compilationInfo.sourceFilesClasspath.forEach { (srcFile, classpath) ->
//        val cp = classpath.joinToString(separator = ":")
//
//        Telemetry.log("CLASSPATH ${cp.length / 1000}k symbols")
//
//        val lines = exec("javac -encoding utf-8 -cp $cp -d ~/greencat/tmp $srcFile")
//        val hasError = lines.find { line -> line.contains("error: ") } != null
//
//        if (hasError) {
//            lines.forEach { line -> Telemetry.err("[JAVAC] $line") }
//        } else {
//            lines.forEach { line -> Telemetry.log("[JAVAC] $line") }
//            Telemetry.log("[JAVAC] Build successful")
//        }
//    }
//    Telemetry.log("\nListing compiled .class files:")
//    val classFilePaths = exec("find ~/greencat/tmp -name '*.class'", print = true)
//
//    Telemetry.log("\nLooking for D8")
//    val d8ToolPath = File("$androidSdkPath/build-tools").run {
//        if (!exists()) {
//            error("No build-tools directory in Android SDK: $absolutePath")
//        }
//        val dirs = listFiles { file -> file.isDirectory }
//
//        if (dirs.isNullOrEmpty()) {
//            error("No build tools installed")
//        }
//        val buildToolsDir = dirs.sortedDescending()[0]
//        val d8ToolPath = "${buildToolsDir.absolutePath}/d8"
//
//        if (File(d8ToolPath).exists()) {
//            Telemetry.log("Using $d8ToolPath")
//        } else {
//            error("No D8 tool found in ${buildToolsDir.absolutePath}")
//        }
//        d8ToolPath
//    }
//    Telemetry.log("\nDexing...")
//    Telemetry.log("Found ${classFilePaths.size} class(es)")
//
//    // See https://stackoverflow.com/questions/30081386/how-to-put-specific-classes-into-main-dex-file!
//    val cmd = "$d8ToolPath ${classFilePaths.filterNot { it.contains("$") }.joinToString(separator = " ")} --output ~/greencat/dex"
//    Telemetry.log("CMD: $cmd")
//    exec(cmd).forEach { line -> Telemetry.log("[D8] $line") }
//
//    exec("echo; ls -lh ~/greencat/dex", print = true)
//}