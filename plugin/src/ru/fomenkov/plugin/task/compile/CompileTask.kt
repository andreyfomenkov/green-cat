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
                val moduleClasspath = checkNotNull(projectInfo.moduleClasspathMap[moduleName]) {
                    "No classpath for module $moduleName"
                }
                val greencatClassDir = "$greencatRoot/$CLASS_FILES_DIR"
                val javaSrcFiles = srcFiles.filter { path -> path.endsWith(".java") }.toSet()
                val kotlinSrcFiles = srcFiles.filter { path -> path.endsWith(".kt") }.toSet()
                val classpath = when (File(greencatClassDir).exists()) {
                    true -> "$greencatClassDir:$moduleClasspath"
                    else -> moduleClasspath
                }
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
        var javac = exec("echo \$JAVA_HOME/bin/javac").first()

        if (!File(javac).exists()) {
            javac = "javac"
        }
        Telemetry.log("Using Java compiler: $javac")
        val lines = exec("$javac -source 1.8 -target 1.8 -encoding utf-8 -g -cp $moduleClasspath -d $classDir $srcFilesLine")
        val inputFileNames = srcFiles.map { path -> File(path).nameWithoutExtension }.toSet()
        val outputFileNames = exec("find $classDir -name '*.class'").map { path -> File(path).nameWithoutExtension }.toSet()

        return when ((inputFileNames - outputFileNames).isEmpty()) {
            true -> CompilationResult.Successful
            else -> CompilationResult.Error(moduleName, lines)
        }
    }

    private fun compileWithKotlin(srcFiles: Set<String>, moduleName: String, moduleClasspath: String): CompilationResult {
        val kotlinc = "$greencatRoot/$KOTLINC_DIR/bin/kotlinc".noTilda()

        if (!File(kotlinc).exists()) {
            error("Kotlin compiler not found: $kotlinc")
        }
        val classDir = "$greencatRoot/$CLASS_FILES_DIR".noTilda()
        val srcFilesLine = srcFiles.joinToString(separator = " ")
        val moduleNameArg = "-module-name ${moduleName.replace("-", "_")}_debug"
        val friendPaths = getFriendModulePaths(moduleName, moduleClasspath).joinToString(separator = ",")
        val cmd = "$kotlinc -Xjvm-default=all-compatibility -Xfriend-paths=$friendPaths $moduleNameArg -d $classDir -classpath $moduleClasspath $srcFilesLine"
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
        val buildToolsDir = getBuildToolsDir()
        val d8ToolPath = "$buildToolsDir/d8"
        val dexDumpToolPath = "$buildToolsDir/dexdump"

        if (File(dexFilePath).exists()) {
            error("DEX file is not cleared")
        }
        val allDirs = exec("find $classDir -type d")
        val classDirs = mutableSetOf<String>()

        if (allDirs.isEmpty()) {
            error("No subdirectories found")
        }
        allDirs.forEach { dir ->
            val files = File(dir).list() ?: emptyArray()
            val hasClassFiles = files.firstOrNull { file -> file.endsWith(".class") } != null

            if (hasClassFiles) {
                classDirs += "$dir/*.class"
            }
        }
        if (classDirs.isEmpty()) {
            error("No subdirectories with class files found")
        }
        val inputDirs = classDirs.joinToString(separator = " ")
        val buildDirs = mutableSetOf<String>()
        buildDirs += classDir

        // TODO: optimize - include only related build dirs

        projectInfo.sourceFilesMap.keys.forEach { moduleName ->
            val moduleClasspath = checkNotNull(projectInfo.moduleClasspathMap[moduleName]) {
                "No classpath for module: $moduleName"
            }
            buildDirs += moduleClasspath.split(":")
                .filter { path -> path.contains("/build/") }
                .filter { path -> File(path).exists() }
                .filter { path -> File(path).isDirectory }
        }
        val classpath = buildDirs.joinToString(separator = " --classpath ")
        val output = exec("$d8ToolPath $inputDirs --intermediate --output $dexDir --classpath $classpath")
        val entries = exec("$dexDumpToolPath $dexDir/$standardDexFileName | grep 'Class descriptor'")
        Telemetry.log("Output DEX file contains ${entries.size} class entries:\n")

        entries.forEach { line ->
            val startIndex = line.indexOfFirst { c -> c == '\'' }
            val endIndex = line.indexOfLast { c -> c == ';' }

            if (startIndex == -1 || endIndex == -1) {
                error("Failed to parse dexdump output")
            }
            Telemetry.log(" # ${line.substring(startIndex + 1, endIndex)}")
        }
        Telemetry.log("")

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

    private fun getBuildToolsDir() = File("$androidSdkRoot/build-tools").run {
        if (!exists()) {
            error("No build-tools directory in Android SDK: $absolutePath")
        }
        val dirs = listFiles { file -> file.isDirectory }

        if (dirs.isNullOrEmpty()) {
            error("No build tools installed")
        }
        dirs.sortedDescending()[0].absolutePath
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