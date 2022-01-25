package ru.fomenkov.plugin.task.resolve

import ru.fomenkov.plugin.resolver.Dependency
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.CURRENT_DIR
import ru.fomenkov.plugin.util.Telemetry
import kotlin.math.abs

class ProjectResolveTask(
    private val input: ProjectResolverInput,
) : Task<ProjectResolverInput, ProjectResolverOutput>(input) {

    override fun body(): ProjectResolverOutput {
        val resolver = ProjectResolver(
            propertiesFileName = input.propertiesFileName,
            settingsFileName = input.settingsFileName,
            ignoredModules = input.ignoredModules,
            ignoredLibs = input.ignoredLibs,
        )
        val properties = resolver.parseGradleProperties().toMutableMap() // TODO: refactor
        val moduleDeclarations = resolver.parseModuleDeclarations()
        Telemetry.log("Project has ${moduleDeclarations.size} module(s)")

        val resources = resolver.findAllResourcesInGradleCache(GRADLE_CACHE_PATH)
        Telemetry.verboseLog("Total ${resources.size} resources in Gradle cache")

        Telemetry.log("Resolving library versions and JAR/AAR artifacts in Gradle cache")
        val resolvedLibs = mutableMapOf<String, String>()
        val cachePaths = mutableMapOf<String, Set<String>>()
        val moduleDependencies = mutableMapOf<String, Set<Dependency>>()

        moduleDeclarations.forEach { declaration ->
            resolver.apply {
                val modulePath = declaration.path
                val deps = parseModuleBuildGradleFile(modulePath, properties)
                moduleDependencies += modulePath to deps
                resolvedLibs += validateAndResolveLibraryVersions(modulePath, deps, properties, moduleDeclarations)
                getArtifactArchivePaths(resolvedLibs, resources, cachePaths)
            }
        }
        Telemetry.log("Resolving project dependencies")
        val moduleNameToPathMap = moduleDeclarations.associate { declaration -> // TODO: refactor
            declaration.name to declaration.path
        }
        if (input.sourceFiles.isEmpty()) {
            error("No source files to compile")
        }
        Telemetry.log("\nChanges to be compiled:\n")
        val sourceFilesClasspath = mutableMapOf<String, Set<String>>()
        val sourceFilesCompileOrder = mutableMapOf<String, Int>()

        input.sourceFiles.forEach { absoluteSrcPath ->
            val relativeSrcPath = absoluteSrcPath.substring(CURRENT_DIR.length + 1, absoluteSrcPath.length)
            val index = relativeSrcPath.indexOf("/src")

            if (index == -1) {
                error("Failed to parse module path")
            }
            val modulePath = relativeSrcPath.substring(0, index)
            val supportMessage = when (isFileSupported(absoluteSrcPath)) {
                true -> "(OK)"
                else -> "(NOT SUPPORTED)"
            }
            check(moduleDependencies.containsKey(modulePath)) { "No module found with path: $modulePath" }
            Telemetry.log(" - [$modulePath] $relativeSrcPath $supportMessage")

            val deps = resolver.getAllModuleDependencies(
                modulePath = modulePath,
                modules = moduleDependencies,
                moduleNameToPath = moduleNameToPathMap,
            )
            val classpath = resolver.buildClasspath(
                androidSdkPath = input.androidSdkPath,
                deps = deps,
                cachePaths = cachePaths,
                moduleNameToPathMap = moduleNameToPathMap,
            )
            sourceFilesClasspath += absoluteSrcPath to classpath
            sourceFilesCompileOrder += absoluteSrcPath to 0 // TODO: implement
        }
        Telemetry.log("")

        // TODO: hack => get all project classpath
//        val deps = mutableSetOf<Dependency>()
//        moduleDependencies.values.forEach { ////////
//            deps += it
//                .filterNot { it is Dependency.Project && resolver.isIgnoredModule(it.moduleName) }
//                .filterNot { it is Dependency.Library && it.relation == Relation.ANDROID_TEST_IMPLEMENTATION }
//                .filterNot { it is Dependency.Library && it.relation == Relation.TEST_IMPLEMENTATION }
//        }
        return ProjectResolverOutput(sourceFilesClasspath, sourceFilesCompileOrder)
    }

    private fun isFileSupported(path: String) = path.trim().endsWith(".java")

    private companion object {
        const val GRADLE_CACHE_PATH = "~/.gradle/caches/modules-2/files-2.1" // TODO: suffixes can be different
    }
}