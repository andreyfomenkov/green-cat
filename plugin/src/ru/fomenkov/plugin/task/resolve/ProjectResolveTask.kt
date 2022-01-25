package ru.fomenkov.plugin.task.resolve

import ru.fomenkov.plugin.project.Module
import ru.fomenkov.plugin.resolver.Dependency
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec

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
        val properties = resolver.parseGradleProperties().toMutableMap() // TODO: refactor
        val moduleDeclarations = resolver.parseModuleDeclarations()
        Telemetry.log("Project has ${moduleDeclarations.size} module(s)")

        val resources = resolver.findAllResourcesInGradleCache(GRADLE_CACHE_PATH)
        Telemetry.verboseLog("Total ${resources.size} resources in Gradle cache")

        Telemetry.log("Resolving library versions and JAR/AAR artifacts in Gradle cache")
        val resolvedLibs = mutableMapOf<String, String>()
        val cachePaths = mutableMapOf<String, Set<String>>()
        val moduleDependencies = mutableMapOf<String, Set<Dependency>>()
        val moduleChildProjects = mutableMapOf<String, MutableSet<String>>()
        val moduleParentProjects = mutableMapOf<String, MutableSet<String>>()

        moduleDeclarations.forEach { declaration ->
            resolver.apply {
                val modulePath = declaration.path
                val deps = parseModuleBuildGradleFile(modulePath, properties)
                moduleDependencies += modulePath to deps
                resolvedLibs += validateAndResolveLibraryVersions(modulePath, deps, properties, moduleDeclarations)
                moduleChildProjects += declaration.path to mutableSetOf()
                moduleParentProjects += declaration.path to mutableSetOf()
                getArtifactArchivePaths(resolvedLibs, resources, cachePaths)
            }
        }
        Telemetry.log("Resolving project dependencies")
        val moduleNameToPathMap = moduleDeclarations.associate { declaration -> // TODO: refactor
            declaration.name to declaration.path
        }

        // TODO: for debugging purposes
//        moduleDeclarations.forEach { declaration ->

//            Telemetry.log("# MODULE: ${declaration.path} #")
//            deps.forEach { path -> Telemetry.log(" - $path") }
//            Telemetry.log("")
//        }
        //


        val dec = moduleDeclarations.find { dec -> dec.name == "" }
        checkNotNull(dec) { "Declaration is null" }

        val deps = resolver.getModuleDependencies(
            modulePath = dec.path,
            modules = moduleDependencies,
            moduleNameToPath = moduleNameToPathMap,
        )
        Telemetry.log("_______________________ MODULE: ${dec.name} _______________________________")
        deps
            .sortedBy { it.javaClass.simpleName }
            .forEach { dep -> Telemetry.log("[DEP] $dep") }

        val classpath = resolver.buildClasspath(
            androidSdkPath = "/Users/andrey.fomenkov/Library/Android/sdk",
            deps = deps,
            cachePaths = cachePaths,
            moduleNameToPathMap = moduleNameToPathMap,
        ).let { paths ->
            val builder = StringBuilder()
            paths.forEach { path -> builder.append("$path:") }
            builder.toString()
        }
        Telemetry.log("_______________________ COMPILATION _______________________________")

        Telemetry.log("_______________________ CLASSPATH _______________________________")
        Telemetry.log(classpath.replace(":", "\n"))

//        moduleDependencies.forEach { (modulePath, deps) ->
//            val childProjects = checkNotNull(moduleChildProjects[modulePath]) {
//                "No key for module path $modulePath found in child projects"
//            }
//            deps.forEach { dependency ->
//                if (dependency is Dependency.Project && !resolver.isIgnoredModule(dependency.moduleName)) {
//                    // TODO: refactor
//                    val child = checkNotNull(moduleNameToPathMap[dependency.moduleName]) {
//                        "No module path for name: ${dependency.moduleName}"
//                    }
//                    childProjects += child
//
//                    val parentProjects = checkNotNull(moduleParentProjects[child]) {
//                        "No key for module path $child found in parent projects"
//                    }
//                    parentProjects += modulePath
//                    moduleParentProjects[child] = parentProjects
//                }
//            }
//            moduleChildProjects[modulePath] = childProjects
//        }
//        Telemetry.log("Building project dependency graph")
        val graph = mutableSetOf<Module>()
//
//        moduleDeclarations.forEach { declaration ->
//            val modulePath = declaration.path
//
//            graph += Module(
//                name = declaration.name,
//                path = modulePath,
//                children = mutableSetOf(),
//                parents = mutableSetOf(),
//                libraries = mutableSetOf(),
//            )
//        }
        return ProjectGraph(graph)
    }

    private companion object {
        const val GRADLE_CACHE_PATH = "~/.gradle/caches/modules-2/files-2.1"
    }
}