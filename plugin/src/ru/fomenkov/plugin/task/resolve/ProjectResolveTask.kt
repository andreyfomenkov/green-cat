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

    // TODO: some deps in settings.gradle depend on local.properties file. Ignore for the first time
    private val ignoredModules = setOf(
        "gl-ndk-renderer",
        "facedetect",
    )

    override fun body(): ProjectDependencies {
        val resolver = ProjectResolver(
            propertiesFileName = input.propertiesFileName,
            settingsFileName = input.settingsFileName,
        )
        val properties = resolver.parseGradleProperties()
        val moduleDeclarations = resolver.parseModuleDeclarations()
        Telemetry.log("Total ${moduleDeclarations.size} module(s)")

        //
        val jars = resolver.findAllJarsInGradleCache(GRADLE_CACHE_PATH)
        val aars = resolver.findAllAarsInGradleCache(GRADLE_CACHE_PATH)
        Telemetry.verboseLog("Total JARs: ${jars.size}, AARs: ${aars.size}")
        //

        moduleDeclarations.forEach { declaration ->
            val moduleName = declaration.path
            val deps = resolver.parseModuleBuildGradleFile(moduleName)
            val resolvedLibs = validateAndResolveLibraryVersions(moduleName, deps, properties, moduleDeclarations)
            getArtifactArchivePaths(resolvedLibs, jars + aars)
        }
        Telemetry.log("Project resolving is complete")
        return ProjectDependencies
    }

    private fun isIgnoredModule(moduleName: String) = ignoredModules.contains(moduleName)

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
        moduleName: String,
        deps: Set<Dependency>,
        properties: Map<String, String>,
        moduleDeclarations: Set<ModuleDeclaration>,
    ): Map<String, String> {
        Telemetry.verboseLog("[$moduleName] Validating module declarations")
        moduleDeclarations.forEach { declaration ->
            val path = "${declaration.path}/$GRADLE_BUILD_FILE"
            if (!File(path).exists()) {
                error("File not found: $path")
            }
        }
        Telemetry.verboseLog("[$moduleName] Resolving module dependencies")
        val moduleNames = moduleDeclarations.map { declaration -> declaration.name }.toSet()
        val resolvedLibs = mutableMapOf<String, String>()

        deps.forEach { dependency ->
            when (dependency) {
                is Dependency.Files -> {
                    val path = dependency.moduleName + "/" + dependency.filePath
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
                    val placeholderOrVersion = dependency.version
                    val version = if (isVersionResolved(placeholderOrVersion)) {
                        placeholderOrVersion
                    } else {
                        properties[placeholderOrVersion]
                    }
                    if (version == null) {
                        Telemetry.err("[$moduleName] No placeholder version: $placeholderOrVersion for $moduleName")
                    } else {
                        resolvedLibs += artifact to version
                    }
                }
            }
        }
        Telemetry.verboseLog("[$moduleName] Validation is OK")
        return resolvedLibs
    }

    private fun isVersionResolved(version: String) = version[0].isDigit()

    private companion object {
        const val GRADLE_BUILD_FILE = "build.gradle"
        const val GRADLE_CACHE_PATH = "~/.gradle/caches/modules-2/files-2.1"
    }
}