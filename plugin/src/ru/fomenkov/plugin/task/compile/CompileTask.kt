package ru.fomenkov.plugin.task.compile

import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.task.resolve.ProjectResolverOutput
import ru.fomenkov.plugin.util.*
import ru.fomenkov.runner.*
import java.io.File
import java.util.concurrent.*

class CompileTask(
    private val greencatRoot: String,
    private val androidSdkRoot: String,
    private val deviceApiLevel: String,
    private val mappedModules: Map<String, String>,
    private val projectInfo: ProjectResolverOutput,
    private val executor: ExecutorService,
    private val showErrorLogs: Boolean,
) : Task<Unit> {

    // For debugging purposes. Find all source files in the module and compile one by one
    private val debugCompileModule = false
    private val debugModuleName = ""

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
            if (debugCompileModule) {
                compileModuleForDebug(debugModuleName)
            } else {
                Telemetry.log("Compilation round ${order + 1}/${orderMap.size}: ${modules.joinToString(separator = ", ")}")
                val tasks = modules.map { moduleName -> compile(moduleName) }

                tasks.forEach { task ->
                    val result = task.get()

                    if (result is CompilationResult.Error) {
                        if (showErrorLogs) {
                            result.output.forEach { line -> Telemetry.err(line) }
                        }
                        deleteClasspathForModule(result.moduleName)
                        error("Failed to compile module ${result.moduleName}")
                    }
                }
            }
        }
        if (!debugCompileModule) {
            runD8()
        }
    }

    private fun compileModuleForDebug(moduleName: String) {
        var moduleClasspathFile = File("$greencatRoot/$CLASSPATH_DIR/$moduleName")
        val modulePath = "$CURRENT_DIR/$moduleName"

        var moduleClasspath = projectInfo.moduleClasspathMap[moduleName]

        if (moduleClasspath == null) {
            val mappedModuleName = mappedModules[moduleName]
            moduleClasspath = projectInfo.moduleClasspathMap[mappedModuleName]
            moduleClasspathFile = File("$greencatRoot/$CLASSPATH_DIR/$mappedModuleName")
            checkNotNull(moduleClasspath) { "No classpath for module $moduleName" }
        }
        if (moduleClasspathFile.exists()) {
            Telemetry.log("\n# Starting debug compilation for module $moduleName ($modulePath) #")
        } else {
            error("No classpath file for module $moduleName")
        }
        val javaFilePaths = exec("find $modulePath -name '*.java'")
            .filterNot { path -> path.contains("/build/") }

        val kotlinFilePaths = exec("find $modulePath -name '*.kt'")
            .filterNot { path -> path.contains("/build/") }

        val allSourcePaths = javaFilePaths + kotlinFilePaths
        val greencatClassDirs = getGreenCatClassDirectories(greencatRoot)
        val classpath = greencatClassDirs.joinToString(separator = ":") + ":$moduleClasspath"

        Telemetry.log("Total: ${javaFilePaths.size} Java file(s) and ${kotlinFilePaths.size} Kotlin file(s)\n")
        Telemetry.log("Classpath size = ${moduleClasspath.length}")

        allSourcePaths.forEachIndexed { index, path ->
            val result = when {
                path.endsWith(".java") -> compileWithJavac(setOf(path), moduleName, classpath)
                path.endsWith(".kt") -> compileWithKotlin(setOf(path), moduleName, classpath)
                else -> null
            }
            when (result) {
                is CompilationResult.Successful -> {
                    Telemetry.log("[${index + 1} / ${allSourcePaths.size}] $path is OK")
                }
                is CompilationResult.Error -> {
                    Telemetry.log("\n[${index + 1} / ${allSourcePaths.size}] $path is FAILED")
                    result.output.forEach { line -> Telemetry.log(" # $line") }
                    Telemetry.log("\n")
                }
                else -> Telemetry.log("[${index + 1} / ${allSourcePaths.size}] $path SKIPPED")
            }
        }
    }

    private fun compile(moduleName: String): Future<CompilationResult> {
        val srcFiles = checkNotNull(projectInfo.sourceFilesMap[moduleName]) {
            "No source files for module $moduleName"
        }
        val moduleClasspath = checkNotNull(projectInfo.moduleClasspathMap[moduleName]) {
            "No classpath for module $moduleName"
        }
        val javaSrcFiles = srcFiles.filter { path -> path.endsWith(".java") }.toSet()
        val kotlinSrcFiles = srcFiles.filter { path -> path.endsWith(".kt") }.toSet()
        val greencatClassDirs = getGreenCatClassDirectories(greencatRoot)
        val classpath = greencatClassDirs.joinToString(separator = ":") + ":$moduleClasspath"

        return Callable {
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

    private fun getGreenCatClassDirectories(greencatRoot: String): Set<String> {
        val files = File("$greencatRoot/$CLASS_FILES_DIR")
            .listFiles { file, _ -> file.isDirectory } ?: emptyArray()
        return files.map { file -> file.absolutePath }.toSet()
    }

    private fun deleteClasspathForModule(moduleName: String) {
        Telemetry.log("Delete classpath file for module $moduleName")
        File("$greencatRoot/$CLASSPATH_DIR/$moduleName").delete()
    }

    private fun compileWithJavac(srcFiles: Set<String>, moduleName: String, moduleClasspath: String): CompilationResult {
        val classDir = "$greencatRoot/$CLASS_FILES_DIR/$moduleName".noTilda()
        val srcFilesLine = srcFiles.joinToString(separator = " ")
        var javac = exec("echo \$JAVA_HOME/bin/javac").first()

        if (!File(javac).exists()) {
            javac = "javac"
        }
        Telemetry.verboseLog("Using Java compiler: $javac")
        val cmd = "$javac -source 1.8 -target 1.8 -encoding utf-8 -g -cp $moduleClasspath -d $classDir $srcFilesLine"
        val lines = exec(cmd)
        Telemetry.log("Java compilation command length = ${cmd.length}, MAX_ARG_STRLEN = 131072")

        val inputFileNames = srcFiles.map { path -> File(path).nameWithoutExtension }.toSet()
        val outputFileNames = exec("find $classDir -name '*.class'").map { path -> File(path).nameWithoutExtension }.toSet()

        return when ((inputFileNames - outputFileNames).isEmpty()) {
            true -> CompilationResult.Successful
            else -> CompilationResult.Error(moduleName, lines)
        }
    }

    private fun compileWithKotlin(srcFiles: Set<String>, moduleName: String, moduleClasspath: String): CompilationResult {
        val kotlinDir = "$greencatRoot/$KOTLINC_DIR"
        val kotlinc = "$kotlinDir/bin/kotlinc".noTilda()

        if (!File(kotlinc).exists()) {
            error("Kotlin compiler not found: $kotlinc")
        }
        val classDir = "$greencatRoot/$CLASS_FILES_DIR/$moduleName".noTilda()
        val srcFilesLine = srcFiles.joinToString(separator = " ")
        val moduleNameArg = "-module-name ${moduleName.replace("-", "_")}_debug"
        val friendPaths = getFriendModulePaths(moduleName, moduleClasspath).joinToString(separator = ",")
        val cmd = "$kotlinc -Xjvm-default=all-compatibility -Xfriend-paths=$friendPaths $moduleNameArg -d $classDir -classpath $moduleClasspath $srcFilesLine"
        Telemetry.log("Kotlin compilation command length = ${cmd.length}, MAX_ARG_STRLEN = 131072")

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
        val buildToolsDir = getBuildToolsDir()
        val d8ToolPath = "$buildToolsDir/d8"
        val dexDumpToolPath = "$buildToolsDir/dexdump"
        val classDir = "$greencatRoot/$CLASS_FILES_DIR".noTilda()
        val moduleDirs = File(classDir).list { file, _ -> file.isDirectory } ?: emptyArray()
        val dexDir = "$greencatRoot/$DEX_FILES_DIR".noTilda()
        val standardDexFileName = "classes.dex"
        val dexFilePath = "$dexDir/$OUTPUT_DEX_FILE".noTilda()
        val currentApiLevel = try {
            deviceApiLevel.toInt()
        } catch (_: Throwable) {
            error("Failed to parse device API level: $deviceApiLevel")
        }
        val minApiLevelArg = if (currentApiLevel >= MIN_API_LEVEL) {
            "--min-api $MIN_API_LEVEL"
        } else {
            ""
        }
        check(moduleDirs.isNotEmpty()) { "Class directory is empty" }
        Telemetry.log("Running D8 (API level: $deviceApiLevel)...")

        val tasks = moduleDirs.map { moduleDir ->
            val task = Callable {
                val outDir = File("$dexDir/$moduleDir")
                val classDirs = exec("find $classDir/$moduleDir -name '*.class'")
                    .map { path -> "${File(path).parentFile.absolutePath}/*.class" }
                    .toSet()
                    .joinToString(separator = " ")

                if (!outDir.exists()) {
                    outDir.mkdir()
                }
                exec("$d8ToolPath $classDirs --file-per-class --output ${outDir.absolutePath} $minApiLevelArg")
                    .forEach { line -> Telemetry.log("[$moduleDir] D8: ${line.trim()}") }
                exec("find ${outDir.absolutePath} -name '*.dex'").isNotEmpty()
            }
            executor.submit(task)
        }
        tasks.forEach { future ->
            val isOk = try {
                future.get()
            } catch (error: Throwable) {
                error("Error running D8 (message = ${error.message})")
            }
            if (!isOk) {
                error("Error running D8")
            }
        }
        val dexFiles = exec("find $dexDir -name '*.dex'")

        if (dexFiles.isEmpty()) {
            error("No DEX files found")
        }
        val dexFilesArg = dexFiles.joinToString(separator = " ") { path -> "'$path'" }

        exec("$d8ToolPath $dexFilesArg --output $dexDir $minApiLevelArg")
            .forEach { line -> Telemetry.log("Merge D8: ${line.trim()}") }

        val entries = exec("$dexDumpToolPath $dexDir/$standardDexFileName | grep 'Class descriptor'")
        Telemetry.log("\nOutput DEX file contains ${entries.size} class entries:\n")

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

    private companion object {
        // Min API level, when there are no warnings about missing types for desugaring. Specifying types in D8
        // classpath fixes the problem, but slows down build
        // TODO: need research about desugaring
        const val MIN_API_LEVEL = 24
    }
}