package ru.fomenkov.plugin.task.compile

import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.task.resolve.ProjectResolverOutput
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.noTilda
import ru.fomenkov.runner.*
import java.io.File
import java.util.concurrent.*

class CompileTask(
    private val greencatRoot: String,
    private val androidSdkRoot: String,
    private val projectInfo: ProjectResolverOutput,
    private val executor: ExecutorService,
) : Task<Unit> {

    override fun run() {
        clearDirectory(CLASS_FILES_DIR)
        clearDirectory(DEX_FILES_DIR)
        val orderMap = mutableMapOf<Int, MutableSet<String>>()

        projectInfo.moduleCompilationOrderMap.forEach { (moduleName: String, order: Int) ->
            val modules = orderMap[order] ?: mutableSetOf()
            modules += moduleName
            orderMap[order] = modules
        }
        orderMap.keys.sorted().forEach { order ->
            val modules = checkNotNull(orderMap[order]) {
                "No modules for compilation order $order"
            }
            Telemetry.log("Compilation round ${order + 1} of ${orderMap.size}: ${modules.joinToString(separator = ", ")}")

            val tasks = modules.map { moduleName ->
                val srcFiles = checkNotNull(projectInfo.sourceFilesMap[moduleName]) {
                    "No source files for module $moduleName"
                }
                val classpath = checkNotNull(projectInfo.moduleClasspathMap[moduleName]) {
                    "No classpath for module $moduleName"
                }
                val javaSrcFiles = srcFiles.filter { path -> path.endsWith(".java") }.toSet()
                val kotlinSrcFiles = srcFiles.filter { path -> path.endsWith(".kt") }.toSet()

                Callable {
                    val result = when (javaSrcFiles.isEmpty()) {
                        true -> CompilationResult.Successful
                        else -> compileWithJavac(srcFiles = javaSrcFiles, moduleName = moduleName, moduleClasspath = classpath)
                    }
                    if (result is CompilationResult.Error) {
                        result
                    } else {
                        when (kotlinSrcFiles.isEmpty()) {
                            true -> CompilationResult.Successful
                            else -> compileWithKotlin(srcFiles = kotlinSrcFiles, moduleName = moduleName, moduleClasspath = classpath)
                        }
                    }
                }.run { executor.submit(this) }
            }
            tasks.forEach { task ->
                val result = task.get()

                if (result is CompilationResult.Error) {
                    result.output.forEach { line -> Telemetry.err(line) }
                    deleteClasspathForModule(result.moduleName)
                    error("Failed to compile module ${result.moduleName}")
                }
            }
        }
        runD8()
    }

    private fun deleteClasspathForModule(moduleName: String) {
        Telemetry.log("Delete classpath file for module $moduleName")
        File("$greencatRoot/$CLASSPATH_DIR/$moduleName").delete()
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

    private fun compileWithKotlin(srcFiles: Set<String>, moduleName: String, moduleClasspath: String): CompilationResult {
        val kotlinc = "$greencatRoot/$KOTLINC_RELAXED_DIR/bin/kotlinc".noTilda()

        if (!File(kotlinc).exists()) {
            error("Kotlin compiler not found: $kotlinc")
        }
        val classDir = "$greencatRoot/$CLASS_FILES_DIR".noTilda()
        val srcFilesLine = srcFiles.joinToString(separator = " ")
        val friendPaths = getFriendModulePaths(moduleName, moduleClasspath).joinToString(separator = ",")
        val cmd = "$kotlinc -Xjvm-default=all-compatibility -Xfriend-paths=$friendPaths -d $classDir -classpath $moduleClasspath $srcFilesLine"
        val lines = exec(cmd)
        val inputFileNames = srcFiles.map { path -> File(path).nameWithoutExtension }.toSet()
        val outputFileNames = exec("find $classDir -name '*.class'").map { path -> File(path).nameWithoutExtension }.toSet()

        return when ((inputFileNames - outputFileNames).isEmpty()) {
            true -> CompilationResult.Successful
            else -> CompilationResult.Error(moduleName, lines)
        }
    }

    private fun getFriendModulePaths(moduleName: String, moduleClasspath: String) =
        moduleClasspath.split(":").filter { path -> path.contains("$moduleName/build") }

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