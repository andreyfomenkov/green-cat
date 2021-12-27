package ru.fomenkov.plugin.task.resolve

import ru.fomenkov.plugin.resolver.Dependency
import ru.fomenkov.plugin.resolver.ModuleDeclaration
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.Telemetry
import java.io.File

class ProjectResolveTask(
    private val input: GradleProjectInput,
) : Task<GradleProjectInput, ProjectDependencies>(input) {

    override fun body(): ProjectDependencies {
        val resolver = ProjectResolver(
            propertiesFileName = input.propertiesFileName,
            settingsFileName = input.settingsFileName,
        )
        val properties = resolver.parseGradleProperties()
        val moduleDeclarations = resolver.parseModuleDeclarations()
        Telemetry.log("Total ${moduleDeclarations.size} module(s)")

        moduleDeclarations.forEach { declaration -> // TODO: check for all modules
            val moduleName = declaration.name
            val deps = resolver.parseModuleBuildGradleFile(moduleName)
            validateDependencies(moduleName, deps, properties, moduleDeclarations)
        }
        return ProjectDependencies
    }

    private fun validateDependencies(
        moduleName: String,
        deps: List<Dependency>,
        properties: Map<String, String>,
        moduleDeclarations: Set<ModuleDeclaration>,
    ) {
        Telemetry.verboseLog("Validating module declarations for $moduleName")
        moduleDeclarations.forEach { declaration ->
            val path = "${declaration.path}/$GRADLE_BUILD_FILE"
            if (!File(path).exists()) {
                error("File not found: $path")
            }
        }
        Telemetry.verboseLog("Resolving module dependencies")
        val moduleNames = moduleDeclarations.map { declaration -> declaration.name }.toSet()
        val resolvedLibs = mutableMapOf<String, String>()

        deps.forEach { dependency ->
            when (dependency) {
                is Dependency.Project -> {
                    if (!moduleNames.contains(dependency.moduleName)) {
                        error("Module '${dependency.moduleName}' declaration not found")
                    }
                }
                is Dependency.Library -> {
                    val artifact = dependency.artifact
                    val placeholderOrVersion = dependency.version
                    val version = if (isVersionResolved(placeholderOrVersion)) {
                        placeholderOrVersion
                    } else {
                        properties[placeholderOrVersion] ?: error("No placeholder version: $placeholderOrVersion for $moduleName")
                    }
                    resolvedLibs += artifact to version
                }
            }
        }
        Telemetry.verboseLog("Validation is OK")
    }

    private fun isVersionResolved(version: String) = version[0].isDigit()

    private companion object {
        const val GRADLE_BUILD_FILE = "build.gradle"
    }
}