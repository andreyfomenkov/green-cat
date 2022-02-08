package ru.fomenkov.plugin.task.resolve

import ru.fomenkov.plugin.resolver.GradleCacheItem
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.*
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ProjectResolveTask(
    private val input: ProjectResolverInput,
) : Task<ProjectResolverInput, ProjectResolverOutput>(input) {

    override fun body(): ProjectResolverOutput {
        val resolver = ProjectResolver(
            propertiesFileName = input.propertiesFileName,
            settingsFileName = input.settingsFileName,
        )
        val moduleDeclarations = resolver.parseModuleDeclarations()

        Telemetry.log("Project has ${moduleDeclarations.size} module(s)")
        Telemetry.log("Resolving project dependencies...")

        val executor = Executors.newFixedThreadPool(2)
        val supportResFuture = executor.submit(
            Callable { resolver.findAllResourcesInGradleCache(SUPPORT_RESOURCES_CACHE_PATH) }
        )
        val jetifiedResFuture = executor.submit(
            Callable { resolver.findAllJetifiedJarsInGradleCache(JETIFIED_RESOURCES_CACHE_PATH) }
        )
        val supportCacheResources = supportResFuture.get()
        val jetifiedCacheResources = jetifiedResFuture.get()
        val moduleNameToPathMap = moduleDeclarations.associate { it.name to it.path } // TODO: refactor
        executor.shutdown()

        // TODO: fill in sourceFilesClasspath and sourceFilesCompileOrder
        val sourceFilesClasspath = mutableMapOf<String, Set<String>>()
        val sourceFilesCompileOrder = mutableMapOf<String, Int>()

        input.sourceFiles.forEach { sourceFile ->
            val classpath = buildClasspathForSourceFile(
                sourceFilePath = sourceFile,
                androidSdkPath = input.androidSdkPath,
                moduleNameToPathMap = moduleNameToPathMap,
                fileCacheResources = supportCacheResources,
                jetifiedResources = jetifiedCacheResources,
            )
            sourceFilesClasspath += sourceFile to classpath
        }
        // TODO: determine compilation order
        input.sourceFiles.forEach { path -> sourceFilesCompileOrder += path to 0 }

        return ProjectResolverOutput(sourceFilesClasspath, sourceFilesCompileOrder)
    }

    private val mappedModules = mapOf( // TODO: for UI tests
        "ok-android-test" to "odnoklassniki-android",
    )

    private fun getModuleName(sourceFile: String): String {
        val parts = sourceFile.split("/")
        val srcDirIndex = parts.indexOf("src")

        if (srcDirIndex == -1) {
            error("Failed to get module name for source file: $sourceFile")
        }
        val sourceName = parts[srcDirIndex - 1]
        val mappedName = mappedModules[sourceName]
        return mappedName ?: sourceName
    }

    private fun buildClasspathForSourceFile(
        sourceFilePath: String,
        androidSdkPath: String,
        moduleNameToPathMap: Map<String, String>,
        fileCacheResources: Set<GradleCacheItem>,
        jetifiedResources: Map<String, Set<String>>,
    ): Set<String> {
        val sourceFile = File(sourceFilePath)

        if (!sourceFile.exists()) {
            error("Source file doesn't exist: $sourceFilePath")
        }
        val moduleName = getModuleName(sourceFilePath)
        check(moduleNameToPathMap.containsKey(moduleName)) { "No module with name: $moduleName" }

        // TODO: source files from the same module => same classpath
        Telemetry.log("Building classpath for ${sourceFile.name} (module: $moduleName)")

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
        val depsOutputFile = File("$GREENCAT_DEPS_OUTPUT_PATH/$moduleName")

        if (!depsOutputFile.exists()) {
            Telemetry.log("Generating Gradle dependency output for module $moduleName...")
            val outputFilePath = depsOutputFile.absolutePath
            val cmd = "./gradlew $moduleName:dependencies --configuration debugAndroidTestCompileClasspath > $outputFilePath"
            val hasErrors = exec(cmd, print = true).find { line -> line.contains("BUILD FAILED") } != null

            if (hasErrors) {
                exec("rm ${depsOutputFile.absolutePath}")
                error("Failed to generate dependency tree")
            }
        }
        depsOutputFile.readLines()
            .forEach {
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
                        val archives = fileCacheResources.filterIsInstance<GradleCacheItem.Archive>()
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
            .map { path -> path.replace(CURRENT_DIR, ".") }
            .filterNot { path -> path.endsWith("-javadoc.jar") }
            .filterNot { path -> path.endsWith("-sources.jar") }
            .filterNot { path ->
                val file = File(path)
                file.isDirectory && file.list().isNullOrEmpty()
            }
            .map { path ->
                when {
                    path.contains("/.gradle/") -> path.replace(HOME_DIR, "../..")
                    else -> path
                }
            }
            .toSet()
    }

    private fun checkPathExists(path: String) = check(File(path).exists()) { "Path doesn't exist: $path" }

    private companion object {
        // TODO: suffixes can be different
        val SUPPORT_RESOURCES_CACHE_PATH = "~/.gradle/caches/modules-2/files-2.1".noTilda()
        val JETIFIED_RESOURCES_CACHE_PATH = "~/.gradle/caches/transforms-3".noTilda()
        val GREENCAT_DEPS_OUTPUT_PATH = "~/greencat/deps".noTilda()
        val GREENCAT_DEX_PATH = "~/greencat/dex".noTilda()
    }
}