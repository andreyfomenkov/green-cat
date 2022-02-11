package ru.fomenkov.plugin.task.compile

import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.task.resolve.ProjectResolverOutput
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.noTilda
import ru.fomenkov.runner.CLASS_FILES_DIR
import ru.fomenkov.runner.DEX_FILES_DIR
import ru.fomenkov.runner.OUTPUT_DEX_FILE
import java.io.File
import java.io.FileFilter

class CompileTask(
    private val greencatRoot: String,
    private val androidSdkRoot: String,
    private val projectInfo: ProjectResolverOutput,
) : Task<Unit> {

    override fun run() {
        clearDirectory(CLASS_FILES_DIR)
        clearDirectory(DEX_FILES_DIR)

        projectInfo.sourceFilesMap.forEach { (moduleName, srcFiles) ->
            val classpath = projectInfo.moduleClasspathMap[moduleName]
            checkNotNull(classpath) { "No classpath for module: $moduleName" }
            val result = compileWithJavac(srcFiles, moduleName, classpath)

            if (result is CompilationResult.Error) {
                result.output.forEach { line -> Telemetry.err("[JAVAC] $line") }
                error("Failed to compile module ${result.moduleName}")
            }
        }
        runD8()
    }

    private fun compileWithJavac(srcFiles: Set<String>, moduleName: String, moduleClasspath: String): CompilationResult {
        val classDir = "$greencatRoot/$CLASS_FILES_DIR".noTilda()
        val srcFilesLine = srcFiles.joinToString(separator = " ")
        val lines = exec("javac -encoding utf-8 -cp $moduleClasspath -d $classDir $srcFilesLine")
        val inputFileNames = srcFiles.map { path -> File(path).nameWithoutExtension }.toSet()
        val outputFileNames = exec("find $classDir -name '*.class'").map { path -> File(path).nameWithoutExtension }.toSet()

        return when ((inputFileNames - outputFileNames).isEmpty()) {
            true -> CompilationResult.Successful
            else -> CompilationResult.Error(moduleName, lines)
        }
    }

    private fun runD8() {
        val standardDexFileName = "classes.dex"
        val classDir = "$greencatRoot/$CLASS_FILES_DIR".noTilda()
        val dexDir = "$greencatRoot/$DEX_FILES_DIR".noTilda()
        val dexFilePath = "$dexDir/$OUTPUT_DEX_FILE".noTilda()
        val d8ToolPath = findD8Tool()

        if (File(dexFilePath).exists()) {
            error("DEX file is not cleared")
        }
        val classFilePaths = exec("find $classDir -name '*.class'")
            .filterNot { path -> path.contains("$") }
            .joinToString(separator = " ")

        val output = exec("$d8ToolPath $classFilePaths --output $dexDir --classpath $classDir")

        File("$dexDir/$standardDexFileName").apply {
            if (!exists()) {
                output.forEach { line -> Telemetry.err("[D8] $line") }
                error("Failed to generate output DEX file: $path")
            }
            if (!renameTo(File(dexFilePath))) {
                error("Failed to rename: $path -> $dexFilePath")
            }
        }
    }

    private fun findD8Tool() = File("$androidSdkRoot/build-tools").run {
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
            Telemetry.log("Using D8 tool: $d8ToolPath")
        } else {
            error("No D8 tool found in ${buildToolsDir.absolutePath}")
        }
        d8ToolPath
    }

    private fun clearDirectory(dirName: String) {
        val path = "$greencatRoot/$dirName".noTilda()
        exec("rm -rf $path")

        if (File(path).exists()) {
            error("Failed to clear directory: $path")
        }
        if (!File(path).mkdir()) {
            error("Failed to create directory: $path")
        }
    }

    sealed class CompilationResult {

        object Successful : CompilationResult()

        data class Error(val moduleName: String, val output: List<String>) : CompilationResult()
    }
}

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