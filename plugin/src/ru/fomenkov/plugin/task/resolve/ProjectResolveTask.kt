package ru.fomenkov.plugin.task.resolve

import ru.fomenkov.plugin.repository.ClasspathOptimizer
import ru.fomenkov.plugin.repository.MetadataArtifactDependencyResolver
import ru.fomenkov.plugin.resolver.Dependency
import ru.fomenkov.plugin.resolver.ModuleDeclaration
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.resolver.Relation
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.*
import ru.fomenkov.runner.CLASSPATH_DIR
import ru.fomenkov.runner.SOURCE_FILES_DIR
import java.io.File

class ProjectResolveTask(
    private val input: ProjectResolverInput,
    private val artifactResolver: MetadataArtifactDependencyResolver,
) : Task<ProjectResolverOutput> {

    private val resolver = ProjectResolver(
        propertiesFileName = input.propertiesFileName,
        settingsFileName = input.settingsFileName,
    )
    private val classpathOptimizer = ClasspathOptimizer()
    private val moduleDeclarations = mutableSetOf<ModuleDeclaration>()
    private val gradleProperties = mutableMapOf<String, String>()
    private val modulePathsMap = mutableMapOf<String, String>()

    override fun run(): ProjectResolverOutput {
        moduleDeclarations += resolver.parseModuleDeclarations()
        modulePathsMap += moduleDeclarations.associate { dec -> dec.name to dec.path }
        gradleProperties += resolver.parseGradleProperties()

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
            Telemetry.log("Mapped modules:\n")

            input.mappedModules.forEach { (moduleFrom, moduleTo) ->
                Telemetry.log(" - [$moduleFrom] => [$moduleTo]")
            }
            Telemetry.log("")
        }
        Telemetry.log("Project contains ${moduleDeclarations.size} module(s)")
        Telemetry.log("Resolving dependencies...\n")

        val cpDir = "${input.greencatRoot}/$CLASSPATH_DIR".noTilda()
        val allCpFiles = File(cpDir).listFiles(File::isFile)?.map(File::getName)?.toSet() ?: emptySet()
        val absentCpFiles = srcModuleGroups.keys.filterNot { moduleName -> allCpFiles.contains(moduleName) }.toSet()

        if (absentCpFiles.isNotEmpty()) {
            Telemetry.log("No classpath files for the next module(s): " + absentCpFiles.joinToString(separator = ", "))
            Telemetry.log("Building dependency tree. It may take a while...")
            val cpFile = File(cpDir)

            if (!cpFile.exists()) {
                cpFile.mkdir()
            }
            absentCpFiles.forEach(::generateClasspathFile)
        }
        val moduleClasspathMap = mutableMapOf<String, String>()
        val moduleChildren = mutableMapOf<String, Set<String>>()

        srcModuleGroups.keys.forEach { moduleName ->
            val cpFilePath = "${input.greencatRoot}/$CLASSPATH_DIR/$moduleName".noTilda()
            val cpFile = File(cpFilePath)

            if (!cpFile.exists()) {
                error("Classpath file doesn't exist: $cpFilePath")
            }
            val (classpath, children) = cpFile.readLines()
            moduleClasspathMap += moduleName to classpath
            moduleChildren += moduleName to children.split(":").toSet()
        }
        val moduleCompilationOrderMap = CompilationOrderResolver().getModulesCompilationOrder(moduleChildren)
        check(srcModuleGroups.size == moduleCompilationOrderMap.size) { "Missing some modules" }

        moduleCompilationOrderMap.keys.forEach { moduleName ->
            checkNotNull(modulePathsMap[moduleName]) { "Module not found: $moduleName" }
        }
        return ProjectResolverOutput(
            sourceFilesMap = srcModuleGroups,
            moduleClasspathMap = moduleClasspathMap,
            moduleCompilationOrderMap = moduleCompilationOrderMap,
        )
    }

    /**
     * Get module classpath with children modules
     */
    private fun getModuleClasspath(moduleName: String, deps: Set<Dependency>): Pair<Set<String>, Set<String>> {
        val classpath = mutableSetOf<String>()
        val children = mutableSetOf<String>()
        val androidSdkPath = input.androidSdkPath
        val current = Dependency.Project(moduleName = moduleName, relation = Relation.IMPLEMENTATION)
        val projects = deps.filterIsInstance<Dependency.Project>() + current // + current module
        val libs = deps.filterIsInstance<Dependency.Library>()
        val files = deps.filterIsInstance<Dependency.Files>()

        fun String.toClasspath() {
            if (File(this).exists()) {
                classpath += this
            }
        }
        projects.forEach { dep ->
            val modulePath = checkNotNull(modulePathsMap[dep.moduleName]) { "No path for module: ${dep.moduleName}" }
            val buildPath = "$CURRENT_DIR/$modulePath/build"

            // All build subdirectories
            getBuildSubDirectories(buildPath).forEach { path -> path.toClasspath() }

            // Local lib directory if any
            File("$modulePath/libs").apply {
                if (exists()) {
                    exec("find $absolutePath -name '*.jar'").forEach { localJar -> localJar.toClasspath() }
                }
            }
            children += dep.moduleName
        }
        libs.forEach { dep ->
            getLibraryPaths(dep).forEach { path -> path.toClasspath() }
        }
        files.forEach { dep ->
            "${dep.modulePath}/${dep.filePath}".toClasspath()
        }
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
            "${platformDir.absolutePath}/android.jar".toClasspath()
            "${platformDir.absolutePath}/data/res".toClasspath()
        }
        return classpathOptimizer.optimize(classpath) to children
    }

    // TODO: other subdirectories?
    private fun getBuildSubDirectories(buildPath: String) = setOf(
        "$buildPath/intermediates/javac/debug/classes",
        "$buildPath/intermediates/javac/debugAndroidTest/classes",
        "$buildPath/intermediates/compile_r_class_jar/debug/R.jar", // TODO: find 'compile_*' directories
        "$buildPath/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar",
        "$buildPath/tmp/kotlin-classes/debug",
        "$buildPath/tmp/kotlin-classes/main",
        "$buildPath/tmp/kapt3/classes/debug",
        "$buildPath/generated/res/resValues/debug",
        "$buildPath/generated/res/rs/debug",
        "$buildPath/generated/crashlytics/res/debug",
        "$buildPath/generated/res/google-services/debug",
        "$buildPath/classes/java/main",
        "$buildPath/classes/java/debug",
        "$buildPath/classes/kotlin/main",
        "$buildPath/classes/kotlin/debug",
    )

    private fun getLibraryPaths(lib: Dependency.Library): Set<String> {
        var version = gradleProperties[lib.version]

        if (lib.version.isBlank()) {
            version = "" // No version, choose the latest one from the Gradle cache

        } else if (version == null) {
            if (lib.version[0].isDigit()) { // Probably it's a hardcoded version, not placeholder
                version = lib.version
            } else {
                error("Unresolved version or placeholder: ${lib.artifact}:${lib.version}")
            }
        }
        val parts = lib.artifact.split(":")
        val groupId = parts.first()
        val artifactId = parts.last()
        // TODO: optimize library classpath - no recursive resolution for non-transitive dependency
        return artifactResolver.resolvePaths(groupId, artifactId, version)
    }

    /**
     * Generate classpath file for the specific module
     * Line 1: module classpath separated with ":"
     * Line 2: child modules, including transitive, separated with ":"
     */
    private fun generateClasspathFile(moduleName: String) {
        var length = 0
        val time = timeMillis {
            val modulePath = checkNotNull(modulePathsMap[moduleName]) { "No path for module: $moduleName" }
            val moduleDependencies = mutableMapOf<String, Set<Dependency>>()
            val classpathFilePath = "${input.greencatRoot}/$CLASSPATH_DIR/$moduleName".noTilda()

            moduleDeclarations.forEach { module ->
                moduleDependencies += module.path to resolver.parseModuleBuildGradleFile(
                    modulePath = module.path,
                    properties = gradleProperties,
                )
            }
            val deps = resolver.getAllModuleDependencies(
                modulePath = modulePath,
                modules = moduleDependencies,
                moduleNameToPath = modulePathsMap,
            )
            val (classpath, children) = getModuleClasspath(moduleName, deps)
            val content = StringBuilder().apply {
                append("${classpath.joinToString(separator = ":")}\n") // Line 1: module classpath
                append(children.joinToString(separator = ":"))         // Line 2: child modules
            }
            length = classpath.size
            File(classpathFilePath).writeText(content.toString())
        }
        Telemetry.log("Generating classpath for '$moduleName' takes ${formatMillis(time)} (length = $length)")
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
}