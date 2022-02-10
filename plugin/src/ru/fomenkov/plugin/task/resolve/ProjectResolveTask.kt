package ru.fomenkov.plugin.task.resolve

import ru.fomenkov.plugin.resolver.GradleCacheItem
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.util.*
import ru.fomenkov.runner.CLASSPATH_DIR
import ru.fomenkov.runner.SOURCE_FILES_DIR
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ProjectResolveTask(private val input: ProjectResolverInput) {

    private val resolver = ProjectResolver(
        propertiesFileName = input.propertiesFileName,
        settingsFileName = input.settingsFileName,
    )

    fun run(): ProjectResolverOutput {
        val moduleDeclarations = resolver.parseModuleDeclarations()
        val modulePathsMap = moduleDeclarations.associate { dec -> dec.name to dec.path }
        val srcFiles = exec("find ${input.greencatRoot}/$SOURCE_FILES_DIR").filter(::isFileSupported)
        val srcModuleGroups = mutableMapOf<String, MutableSet<String>>() // Module name -> source files

        if (srcFiles.isEmpty()) {
            error("No files to compile")
        } else {
            Telemetry.log("Total ${srcFiles.size} source file(s) to compile:\n")

            srcFiles.forEach { path ->
                val moduleName = getSourceFileModuleName(path)
                check(modulePathsMap.containsKey(moduleName)) { "No path for module name: $moduleName" }

                val paths = srcModuleGroups[moduleName] ?: mutableSetOf()
                paths += path
                srcModuleGroups[moduleName] = paths
                Telemetry.log(" - [$moduleName] $path")
            }
            Telemetry.log("")
        }
        if (input.mappedModules.isNotEmpty()) {
            Telemetry.log("Module mappings:\n")

            input.mappedModules.forEach { (moduleFrom, moduleTo) ->
                Telemetry.log(" - [$moduleFrom] => [$moduleTo]")
            }
            Telemetry.log("")
        }
        Telemetry.log("Project contains ${moduleDeclarations.size} module(s)")
        Telemetry.log("Resolving project dependencies...\n")

        val cpDir = "${input.greencatRoot}/$CLASSPATH_DIR".noTilda()
        val allCpFiles = File(cpDir).listFiles(File::isFile)?.map(File::getName)?.toSet() ?: emptySet()
        val absentCpFiles = srcModuleGroups.keys.filterNot { moduleName -> allCpFiles.contains(moduleName) }.toSet()

        if (absentCpFiles.isNotEmpty()) {
            generateClasspathFilesForModules(absentCpFiles, modulePathsMap)
        }
        val moduleClasspathMap = mutableMapOf<String, String>()
        val moduleCompilationOrderMap = mutableMapOf<String, Int>()

        srcModuleGroups.keys.forEach { moduleName ->
            val cpFilePath = "${input.greencatRoot}/$CLASSPATH_DIR/$moduleName".noTilda()
            val cpFile = File(cpFilePath)

            if (!cpFile.exists()) {
                error("Classpath file doesn't exist: $cpFilePath")
            }
            moduleClasspathMap += moduleName to cpFile.readText()
        }
        // TODO: determine modules' compilation order

        return ProjectResolverOutput(
            sourceFilesMap = srcModuleGroups,
            moduleClasspathMap = moduleClasspathMap,
            moduleCompilationOrderMap = moduleCompilationOrderMap,
        )
    }

    private fun generateClasspathFilesForModules(
        moduleNames: Set<String>,
        moduleNameToPathMap: Map<String, String>,
    ) {
        Telemetry.log("No classpath files for the next module(s): " + moduleNames.joinToString(separator = ", "))
        Telemetry.log("Building dependency tree. It may take a while...")

        val executor = Executors.newFixedThreadPool(4)
        val supportResFuture = executor.submit(
            Callable { resolver.findAllResourcesInGradleCache(SUPPORT_RESOURCES_CACHE_PATH) }
        )
        val jetifiedResFuture = executor.submit(
            Callable { resolver.findAllJetifiedJarsInGradleCache(JETIFIED_RESOURCES_CACHE_PATH) }
        )
        val supportCacheResources = supportResFuture.get()
        val jetifiedCacheResources = jetifiedResFuture.get()

        moduleNames.map { name ->
            val task = Callable {
                generateClasspathFile(
                    moduleName = name,
                    moduleNameToPathMap = moduleNameToPathMap,
                    supportCacheResources = supportCacheResources,
                    jetifiedResources = jetifiedCacheResources,
                )
            }
            executor.submit(task)
        }.forEach { task ->
            val isOk = task.get()

            if (!isOk) {
                error("Failed to generate classpath file")
            }
        }
        executor.shutdown()
    }

    private fun generateClasspathFile(
        moduleName: String,
        moduleNameToPathMap: Map<String, String>,
        supportCacheResources: Set<GradleCacheItem>,
        jetifiedResources: Map<String, Set<String>>,
    ): Boolean {
        fun isGradleRunSuccessful(lines: List<String>) = lines.takeLast(10).find { line ->
            line.trim().startsWith("BUILD SUCCESSFUL")
        } != null

        val output = exec("./gradlew $moduleName:dependencies")

        fun getOffsetForConfiguration(configuration: String): Int {
            for (i in 0 until output.size - 1) {
                val currLine = output[i].trim()
                val nextLine = output[i + 1].trim()

                if (currLine.startsWith(configuration) && nextLine.startsWith("+---")) {
                    return i + 1
                }
            }
            return -1
        }
        if (!isGradleRunSuccessful(output)) {
            Telemetry.err("Failed to generate Gradle dependency tree")
            output.takeLast(50).forEach { line -> Telemetry.err("[Gradle] $line") }
            return false
        }
        val graph = mutableListOf<String>()
        var offset = getOffsetForConfiguration(CONFIGURATION_COMPILE_CLASSPATH_FOR_TEST)

        if (offset == -1) {
            offset = getOffsetForConfiguration(CONFIGURATION_COMPILE_CLASSPATH_FOR_DEBUG)

            if (offset == -1) {
                Telemetry.err("No configurations found: $CONFIGURATION_COMPILE_CLASSPATH_FOR_TEST, $CONFIGURATION_COMPILE_CLASSPATH_FOR_DEBUG")
                return false
            }
        }
        for (i in offset until output.size) {
            if (output[i].isBlank()) {
                break
            }
            graph += output[i]
        }
        val classpath = getModuleClasspath(
            moduleName = moduleName,
            androidSdkPath = input.androidSdkPath,
            graph = graph,
            moduleNameToPathMap = moduleNameToPathMap,
            supportCacheResources = supportCacheResources,
            jetifiedResources = jetifiedResources,
        )
        val cpFile = File("${input.greencatRoot}/$CLASSPATH_DIR/$moduleName".noTilda())
        cpFile.writeText(classpath.joinToString(separator = ":"))
        return true
    }

    private fun getSourceFileModuleName(sourceFile: String): String {
        val parts = sourceFile.split("/")
        val srcDirIndex = parts.lastIndexOf("src")

        if (srcDirIndex == -1) {
            error("Failed to get module name for source file: $sourceFile")
        }
        val sourceName = parts[srcDirIndex - 1]
        val mappedName = input.mappedModules[sourceName]
        return mappedName ?: sourceName
    }

    private fun getModuleClasspath(
        moduleName: String,
        androidSdkPath: String,
        graph: List<String>,
        moduleNameToPathMap: Map<String, String>,
        supportCacheResources: Set<GradleCacheItem>,
        jetifiedResources: Map<String, Set<String>>,
    ): Set<String> {
        val projects = mutableSetOf<String>()
        val libs = mutableSetOf<Pair<String, String>>()
        val resolveProject = { line: String -> // TODO: refactor
            line.substring(line.indexOf(":") + 1, line.length)
                .replace("(*)", "")
                .replace(":", "/")
                .trim()
        }
        val resolveLibrary = { line: String -> // TODO: refactor
            var artifact = line.substring(0, line.lastIndexOf(":"))
                .replace("(*)", "")
                .trim()
            var version = line.substring(line.lastIndexOf(":") + 1, line.length)

            if (version.contains("->")) {
                version = version.substring(version.indexOf("->") + 2, version.length)
            }
            version = version.replace("(c)", "")
                .replace("(*)", "")
                .trim()

            if (!artifact.contains(":")) {
                artifact = line.substring(0, line.indexOf("->")).trim()
            }
            artifact to version
        }
        var insideBlock = false

        graph.forEach {
            var line = it

            if (!insideBlock && line.trim().startsWith("+---")) { // TODO: refactor
                insideBlock = true
            }
            if (insideBlock && line.isBlank()) {
                insideBlock = false
            }
            if (insideBlock) {
                line = line.substring(line.indexOf("- ") + 2, line.length) // TODO: refactor
                when {
                    line.startsWith("project ") -> {
                        projects += resolveProject(line)
                    }
                    else -> {
                        if (line.contains("-> project")) { // TODO: WTF???
                            val parts = line.split("->")
                            val left = parts[0]
                            val right = parts[1]
                            projects += resolveProject(right)
                            libs += resolveLibrary(left)
                        } else {
                            libs += resolveLibrary(line)
                        }
                    }
                }
            }
        }
        projects += moduleName // Add module itself!
        projects.forEach { name ->
            val path = moduleNameToPathMap[name]
            checkNotNull(path) { "No path for project: $name" }
        }
        val jarPaths = mutableSetOf<String>()

        // Libraries from Gradle cache
        libs
            .forEach { (artifact, version) ->
                val index = artifact.indexOf(":")

                if (index == -1) {
                    error("Failed to parse artifact: $artifact")
                }
                val key = artifact.substring(index + 1, artifact.length) + "-$version"

                when (val paths = jetifiedResources[key]) {
                    null -> {
                        val archives = supportCacheResources.filterIsInstance<GradleCacheItem.Archive>()
                            .filter { res -> artifact == "${res.pkg}:${res.artifact}" }

                        if (archives.isEmpty()) {
                            Telemetry.err("No support or jetified resources found for artifact: $key")
                        } else {
                            jarPaths += archives.map { res -> res.fullPath }
                        }
                    }
                    else -> {
                        jarPaths += paths
                    }
                }
            }
        val classpath = mutableSetOf<String>()

        // Android SDK
        File(androidSdkPath).apply {
            if (!exists()) {
                error("No Android SDK found: $androidSdkPath")
            }
            val dirs = File("$androidSdkPath/platforms").listFiles { file -> file.isDirectory } ?: emptyArray()

            if (dirs.isEmpty()) {
                error("No Android platforms installed")
            }
            val platformDir = dirs.sortedDescending()[0]
            classpath += "${platformDir.absolutePath}/android.jar".apply { checkPathExists(this) }
            classpath += "${platformDir.absolutePath}/data/res".apply { checkPathExists(this) }
        }
        // Projects' build directories
        projects.forEach { name ->
            val modulePath = moduleNameToPathMap[name]
            checkNotNull(modulePath) { "No path for module: $name" }
            val buildPath = "$CURRENT_DIR/$modulePath/build"
            checkPathExists(buildPath)

            // TODO: refactor
            "$buildPath/intermediates/javac/debug/classes".apply { if (File(this).exists()) classpath += this }
            "$buildPath/intermediates/javac/debugAndroidTest/classes".apply { if (File(this).exists()) classpath += this }
            "$buildPath/intermediates/compile_r_class_jar/debug/R.jar".apply { if (File(this).exists()) classpath += this }
            "$buildPath/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar".apply { if (File(this).exists()) classpath += this }
            "$buildPath/tmp/kotlin-classes/debug".apply { if (File(this).exists()) classpath += this }
            "$buildPath/tmp/kapt3/classes/debug".apply { if (File(this).exists()) classpath += this }
            "$buildPath/generated/res/resValues/debug".apply { if (File(this).exists()) classpath += this }
            "$buildPath/generated/res/rs/debug".apply { if (File(this).exists()) classpath += this }
            "$buildPath/generated/crashlytics/res/debug".apply { if (File(this).exists()) classpath += this }
            "$buildPath/generated/res/google-services/debug".apply { if (File(this).exists()) classpath += this }

            // Local lib directory if any
            File("$modulePath/libs").apply {
                if (exists()) {
                    exec("find $absolutePath -name '*.jar'").forEach { localJar ->
                        if (File(localJar).exists()) {
                            classpath += localJar
                        }
                    }
                }
            }
        }
        jarPaths.forEach { path -> checkPathExists(path) }
        classpath += jarPaths
        return classpath // TODO: implement filtering
            .asSequence()
            .map { path -> path.replace(CURRENT_DIR, ".") }
            .filterNot { path -> path.endsWith("-javadoc.jar") }
            .filterNot { path -> path.endsWith("-sources.jar") }
            .filterNot { path ->
                val file = File(path)
                file.isDirectory && file.list().isNullOrEmpty()
            }
            .toSet()
    }

    private fun checkPathExists(path: String) = check(File(path).exists()) { "Path doesn't exist: $path" }

    private companion object {
        // TODO: suffixes can be different
        val SUPPORT_RESOURCES_CACHE_PATH = "~/.gradle/caches/modules-2/files-2.1".noTilda()
        val JETIFIED_RESOURCES_CACHE_PATH = "~/.gradle/caches/transforms-3".noTilda()
        const val CONFIGURATION_COMPILE_CLASSPATH_FOR_TEST = "debugAndroidTestCompileClasspath"
        const val CONFIGURATION_COMPILE_CLASSPATH_FOR_DEBUG = "debugCompileClasspath"
    }
}