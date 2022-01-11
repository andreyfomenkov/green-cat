package ru.fomenkov.plugin.task.resolve

import ru.fomenkov.plugin.project.Module
import ru.fomenkov.plugin.resolver.Dependency
import ru.fomenkov.plugin.resolver.ModuleDeclaration
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.Telemetry
import java.io.File

class ProjectResolveTask(
    private val input: GradleProjectInput,
) : Task<GradleProjectInput, ProjectGraph>(input) {

    override fun body(): ProjectGraph {
        val resolver = ProjectResolver(
            propertiesFileName = input.propertiesFileName,
            settingsFileName = input.settingsFileName,
        )
        val properties = resolver.parseGradleProperties()
        val moduleDeclarations = resolver
            .parseModuleDeclarations()
        Telemetry.log("Project has ${moduleDeclarations.size} module(s)")

        val jars = resolver.findAllJarsInGradleCache(GRADLE_CACHE_PATH)
        val aars = resolver.findAllAarsInGradleCache(GRADLE_CACHE_PATH)
        Telemetry.verboseLog("Total JARs: ${jars.size}, AARs: ${aars.size} in Gradle cache")

        val graph = mutableSetOf<Module>()
        val moduleDependencies = moduleDeclarations
            .associate { declaration ->
                val modulePath = declaration.path
                val deps = resolver.parseModuleBuildGradleFile(modulePath)
                modulePath to deps
            }
        val moduleChildProjects = mutableMapOf<String, MutableSet<String>>()
        val moduleParentProjects = mutableMapOf<String, MutableSet<String>>()

        moduleDependencies.forEach { (modulePath, deps) ->
            val childProjects = moduleChildProjects[modulePath] ?: mutableSetOf()

            deps.forEach { dependency ->
                if (dependency is Dependency.Project) {
                    val child = dependency.moduleName
                    childProjects += child

                    val parentProjects = moduleParentProjects[child] ?: mutableSetOf()
                    parentProjects += modulePath
                    moduleParentProjects[child] = parentProjects
                }
            }
            moduleChildProjects[modulePath] = childProjects
        }

//        val moduleLibs = moduleDependencies
//            .mapValues { (modulePath, deps) ->
//                val libs = mutableSetOf<Library>()
//                val versions = validateAndResolveLibraryVersions(modulePath, deps, properties, moduleDeclarations)
//
//                deps.forEach { dependency ->
//                    if (dependency is Dependency.Library) {
//                        val artifact = dependency.artifact
//                        libs += Library(
//                            name = artifact,
//                            version = checkNotNull(versions[artifact]) { "No version for artifact $artifact" },
//                            cachePaths = setOf(), // TODO: add
//                        )
//                    }
//                }
//                libs
//            }
//
        moduleDeclarations.forEach { declaration ->
            val modulePath = declaration.path

            graph += Module(
                name = declaration.name,
                path = modulePath,
                children = mutableSetOf(),
                parents = mutableSetOf(),
                libraries = mutableSetOf(),
//                libraries = checkNotNull(moduleLibs[modulePath]) { "No libs provided for module $modulePath" },
            )
        }

//        moduleDeclarations.forEach { declaration ->
//            val modulePath = declaration.path
//
//            val deps = resolver.parseModuleBuildGradleFile(modulePath)
//            val resolvedLibs = validateAndResolveLibraryVersions(modulePath, deps, properties, moduleDeclarations)
//            val cachePaths = getArtifactArchivePaths(resolvedLibs, jars + aars)
//        }
        ////////

        // commons-persist
        //

        graph.forEach { module ->
            val name = module.name
            val children = moduleChildProjects[name] ?: mutableSetOf()
            val parents = moduleParentProjects[name] ?: mutableSetOf()

            Telemetry.log("\n### Module: $name, children: ${children.size}, parents: ${parents.size}")
            children.forEach { name -> Telemetry.log("[CHILD] $name") }
            parents.forEach { name -> Telemetry.log("[PARENT] $name") }
        }
        ////////
        return ProjectGraph(graph)
    }

    private fun isIgnoredModule(moduleName: String) = input.ignoredModules.contains(moduleName)

    private fun isIgnoredLib(artifact: String) = input.ignoredLibs.contains(artifact)

    /**
     * @param resolvedLibs map of artifact to resolved versions
     * @return map of artifact to resolved paths in Gradle cache
     */
    private fun getArtifactArchivePaths(
        resolvedLibs: Map<String, String>,
        archivePaths: Set<String>,
    ): Map<String, Set<String>> {
        val resolvedPaths = mutableMapOf<String, Set<String>>()

        resolvedLibs.forEach { (artifact, version) ->
            var versionPath = artifact.replace(":", "/") + "/" + version
            var jarPaths = archivePaths.filter { jar -> jar.contains(versionPath) }.toSet()

            if (jarPaths.isEmpty()) {
                val libraryPath = artifact.replace(":", "/")
                val versionPaths = archivePaths.filter { jar -> jar.contains(libraryPath) }
                val versions = versionPaths.map { it.replaceBefore(libraryPath, "")
                    .replace("$libraryPath/", "")
                    .split("/")[0] }
                    .toSet()
                    .toList()
                    .sorted()
                val fallbackVersion = versions.firstOrNull { it > version }

                if (fallbackVersion != null) {
                    versionPath = artifact.replace(":", "/") + "/" + fallbackVersion
                    jarPaths = archivePaths.filter { jar -> jar.contains(versionPath) }.toSet()

                    if (jarPaths.isNotEmpty()) {
                        Telemetry.log("Fallback version: $artifact ($version -> $fallbackVersion)")
                    }
                }
            }
            if (jarPaths.isEmpty()) {
                Telemetry.err("No JARs / AARs found in Gradle cache for artifact: $artifact ($version)")
            }
            resolvedPaths += artifact to jarPaths
        }
        return resolvedPaths
    }

    private fun validateAndResolveLibraryVersions(
        modulePath: String,
        deps: Set<Dependency>,
        properties: Map<String, String>,
        moduleDeclarations: Set<ModuleDeclaration>,
    ): Map<String, String> {
        Telemetry.verboseLog("[$modulePath] Validating module declarations")
        moduleDeclarations.forEach { declaration ->
            val path = "${declaration.path}/$GRADLE_BUILD_FILE"
            if (!File(path).exists()) {
                error("File not found: $path")
            }
        }
        Telemetry.verboseLog("[$modulePath] Resolving module dependencies")
        val moduleNames = moduleDeclarations.map { declaration -> declaration.name }.toSet()
        val resolvedLibs = mutableMapOf<String, String>()

        deps.forEach { dependency ->
            when (dependency) {
                is Dependency.Files -> {
                    val path = dependency.modulePath + "/" + dependency.filePath
                    if (!File(path).exists()) {
                        error("Local file dependency not found: $path")
                    }
                }
                is Dependency.Project -> {
                    val moduleName = dependency.moduleName

                    if (!isIgnoredModule(moduleName) && !moduleNames.contains(moduleName)) {
                        error("Module '${dependency.moduleName}' declaration not found")
                    }
                }
                is Dependency.Library -> {
                    val artifact = dependency.artifact

                    if (!isIgnoredLib(artifact)) {
                        val placeholderOrVersion = dependency.version
                        val version = if (isVersionResolved(placeholderOrVersion)) {
                            placeholderOrVersion
                        } else {
                            properties[placeholderOrVersion]
                        }
                        if (version == null) {
                            Telemetry.err("[$modulePath] No placeholder version: $placeholderOrVersion for $modulePath")
                        } else {
                            resolvedLibs += artifact to version
                        }
                    }
                }
            }
        }
        Telemetry.verboseLog("[$modulePath] Validation is OK")
        return resolvedLibs
    }

    private fun isVersionResolved(version: String) = version[0].isDigit()

    private companion object {
        const val GRADLE_BUILD_FILE = "build.gradle"
        const val GRADLE_CACHE_PATH = "~/.gradle/caches/modules-2/files-2.1"
    }
}