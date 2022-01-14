package ru.fomenkov.plugin.task.resolve

import ru.fomenkov.plugin.project.Module
import ru.fomenkov.plugin.resolver.Dependency
import ru.fomenkov.plugin.resolver.ModuleDeclaration
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.formatMillis
import java.io.File

class ProjectResolveTask(
    private val input: GradleProjectInput,
) : Task<GradleProjectInput, ProjectGraph>(input) {

    override fun body(): ProjectGraph {
        val resolver = ProjectResolver(
            propertiesFileName = input.propertiesFileName,
            settingsFileName = input.settingsFileName,
            ignoredModules = input.ignoredModules,
            ignoredLibs = input.ignoredLibs,
        )
        val properties = resolver.parseGradleProperties()
        val moduleDeclarations = resolver.parseModuleDeclarations()
        Telemetry.log("Project has ${moduleDeclarations.size} module(s)")

        val jars = resolver.findAllResourcesInGradleCache(GRADLE_CACHE_PATH, "jar")
        val aars = resolver.findAllResourcesInGradleCache(GRADLE_CACHE_PATH, "aar")

        Telemetry.verboseLog("Total JARs: ${jars.size}, AARs: ${aars.size} in Gradle cache")
        Telemetry.log("Resolving library versions and JAR/AAR artifacts in Gradle cache")
        val resolvedLibs = mutableMapOf<String, String>()
        val cachePaths = mutableMapOf<String, Set<String>>()
        val moduleDependencies = mutableMapOf<String, Set<Dependency>>()
        val moduleChildProjects = mutableMapOf<String, MutableSet<String>>()
        val moduleParentProjects = mutableMapOf<String, MutableSet<String>>()

        moduleDeclarations.forEach { declaration ->
            resolver.apply {
                val modulePath = declaration.path
                val deps = parseModuleBuildGradleFile(modulePath)
                moduleDependencies += modulePath to deps
                resolvedLibs += validateAndResolveLibraryVersions(modulePath, deps, properties, moduleDeclarations)
                moduleChildProjects += declaration.path to mutableSetOf()
                moduleParentProjects += declaration.path to mutableSetOf()
                getArtifactArchivePaths(resolvedLibs, jars + aars, cachePaths)
            }
        }
        Telemetry.log("Parsing module Gradle build files")
        val graph = mutableSetOf<Module>()

        Telemetry.log("Resolving project dependencies")
        val moduleNameToPathMap = moduleDeclarations.associate { declaration -> // TODO: refactor
            declaration.name to declaration.path
        }
        moduleDependencies.forEach { (modulePath, deps) ->
            val childProjects = checkNotNull(moduleChildProjects[modulePath]) {
                "No key for module path $modulePath found in child projects"
            }
            deps.forEach { dependency ->
                if (dependency is Dependency.Project && !resolver.isIgnoredModule(dependency.moduleName)) {
                    // TODO: refactor
                    val child = checkNotNull(moduleNameToPathMap[dependency.moduleName]) {
                        "No module path for name: ${dependency.moduleName}"
                    }
                    childProjects += child

                    val parentProjects = checkNotNull(moduleParentProjects[child]) {
                        "No key for module path $child found in parent projects"
                    }
                    parentProjects += modulePath
                    moduleParentProjects[child] = parentProjects
                }
            }
            moduleChildProjects[modulePath] = childProjects
        }

        Telemetry.log("Building project dependency graph")
        moduleDeclarations.forEach { declaration ->
            val modulePath = declaration.path

            graph += Module(
                name = declaration.name,
                path = modulePath,
                children = mutableSetOf(),
                parents = mutableSetOf(),
                libraries = mutableSetOf(),
            )
        }

        // TODO: for debugging purposes
        graph.forEach { module ->
            // Check commons-persist!
            val name = module.name
            val children = moduleChildProjects[name] ?: mutableSetOf()
            val parents = moduleParentProjects[name] ?: mutableSetOf()

            Telemetry.log("\n### Module: $name, children: ${children.size}, parents: ${parents.size}")
            children.forEach { name -> Telemetry.log("[CHILD] $name") }
            parents.forEach { name -> Telemetry.log("[PARENT] $name") }
        }
        // TODO: remove
        return ProjectGraph(graph)
    }

    private companion object {
        const val GRADLE_CACHE_PATH = "~/.gradle/caches/modules-2/files-2.1"
    }
}