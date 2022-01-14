package ru.fomenkov.plugin.resolver

import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.isVersionGreaterOrEquals
import java.io.File

class ProjectResolver(
    private val propertiesFileName: String,
    private val settingsFileName: String,
    private val ignoredModules: Set<String> = emptySet(),
    private val ignoredLibs: Set<String> = emptySet(),
) {

    fun parseGradleProperties(): Map<String, String> {
        Telemetry.verboseLog("Parsing $propertiesFileName")
        val map = mutableMapOf<String, String>()

        File(propertiesFileName).readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filterNot { line -> line.isBlank() || line.startsWith("#") }
            .forEach { line ->
                val parts = line.split("=")

                if (parts.size == 2) {
                    val property = parts.first().trim()
                    var value = parts.last().trim()

                    if (value.endsWith("@aar")) {
                        value = value.substring(0, value.length - 4)
                    }
                    map += property to value
                } else {
                    Telemetry.verboseErr("[$propertiesFileName] Skipping line: $line")
                }
            }
        return map
    }

    fun parseModuleDeclarations(): Set<ModuleDeclaration> {
        Telemetry.verboseLog("Parsing $settingsFileName")
        val set = mutableSetOf<ModuleDeclaration>()

        File(settingsFileName).readLines()
            .map { line -> line.trim() }
            .forEach { line ->
                when {
                    line.startsWith("':") -> {
                        val startIndex = line.indexOf(":")
                        val endIndex = line.indexOf("'", 1)

                        if (startIndex == -1 || endIndex == -1) {
                            error("[Module declaration] Failed to parse line: $line")
                        } else {
                            val name = line.substring(startIndex + 1, endIndex).replace(":", "/")
                            set += ModuleDeclaration(name = name, path = name,)
                        }
                    }
                    line.startsWith("'[") -> {
                        val startIndex = line.indexOf("[")
                        val endIndex = line.indexOf("'", 1)

                        if (startIndex == -1 || endIndex == -1) {
                            error("[Module declaration] Failed to parse line: $line")
                        } else {
                            val path = line.substring(startIndex + 1, endIndex).replace("]:", "/")
                            val name = when {
                                path.contains("/") -> path.split("/").last()
                                else -> path
                            }
                            set += ModuleDeclaration(name = name, path = path)
                        }
                    }
                }
            }
        return set
    }

    fun parseModuleBuildGradleFile(modulePath: String): Set<Dependency> {
        Telemetry.verboseLog("Parsing $modulePath/build.gradle")
        val deps = mutableSetOf<Dependency>()
        val path = "$modulePath/$BUILD_GRADLE_FILE_NAME"
        var insideDepsBlock = false

        File(path).readLines()
            .map { line -> line.trim().replace("\"", "'") }
            .filterNot { line -> line.isBlank() || line.startsWith("#") || line.startsWith("//") }
            .forEach { line ->
                if (!insideDepsBlock && line.startsWith(DEPENDENCIES_BLOCK_START)) {
                    insideDepsBlock = true

                } else if (insideDepsBlock && line.startsWith(DEPENDENCIES_BLOCK_END)) {
                    insideDepsBlock = false

                } else if (insideDepsBlock) {
                    val dependency = when {
                        line.hasFileTreeImplementationPrefix() -> null // Ignore fileTree declaration for a while
                        line.hasFilesImplementationPrefix() || line.hasFilesApiPrefix() -> {
                            parseFilesDependency(modulePath, line)
                        }
                        line.hasProjectImplementationPrefix() ||
                                line.hasProjectDebugImplementationPrefix() ||
                                line.hasProjectApiPrefix() ||
                                line.hasProjectCompileOnlyPrefix() ||
                                line.hasProjectAndroidTestImplementationPrefix() ||
                                line.hasProjectTestImplementationPrefix() -> {
                            parseModuleDependency(line)
                        }
                        line.hasLibraryImplementationPrefix() ||
                                line.hasLibraryApiPrefix() ||
                                line.hasLibraryAndroidTestImplementationPrefix() ||
                                line.hasLibraryTestImplementationPrefix() -> {
                            parseLibraryDependency(line)
                        }
                        else -> null
                    }
                    if (dependency != null) {
                        deps += dependency
                    } else {
                        Telemetry.verboseErr("[$path] Skipping line: $line")
                    }
                }
            }
        return deps
    }

    /**
     * @param path Gradle cache root path
     * @param extension resource extension (usually JAR or AAR) without leading dot
     */
    fun findAllResourcesInGradleCache(path: String, extension: String): Set<CacheResource> {
        Telemetry.verboseLog("Get all .$extension files in Gradle cache")
        val homeDir = exec("echo ~").first()
        if (homeDir.isBlank()) {
            error("Failed to parse home directory")
        }
        val fullPath = path.replace("~", homeDir)
        val resources = mutableSetOf<CacheResource>()

        exec("find $path -name '*.$extension'")
            .filter { line -> line.trim().endsWith(".$extension") }
            .forEach { line ->
                val parts = line.substring(fullPath.length + 1, line.length).split("/")
                if (parts.size != 5) {
                    error("[Gradle cache] Failed to parse path: $line")
                }
                resources += CacheResource(
                    pkg = parts[0],
                    artifact = parts[1],
                    version = parts[2],
                    resource = parts[4],
                    fullPath = line,
                )
            }
        return resources
    }

    /**
     * Parse module info and return library artifacts mapped to the
     * appropriate resolved versions instead of placeholders
     */
    fun validateAndResolveLibraryVersions(
        modulePath: String,
        deps: Set<Dependency>,
        properties: Map<String, String>,
        moduleDeclarations: Set<ModuleDeclaration>,
    ): Map<String, String> {
        Telemetry.verboseLog("[$modulePath] Validating module declarations")
        moduleDeclarations.forEach { declaration ->
            val path = "${declaration.path}/$BUILD_GRADLE_FILE_NAME"
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
                        val version = when {
                            placeholderOrVersion.isBlank() -> "" // Use the latest artifact version (workaround for compound version)
                            isVersionResolved(placeholderOrVersion) -> placeholderOrVersion
                            else -> properties[placeholderOrVersion]
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

    /**
     * @param resolvedLibs map of artifact to resolved versions
     * @param cacheResources all JAR / AAR resources in Gradle cache
     * @param cachePaths output map for artifacts to their archive paths in Gradle cache
     */
    fun getArtifactArchivePaths(
        resolvedLibs: Map<String, String>,
        cacheResources: Set<CacheResource>,
        cachePaths: MutableMap<String, Set<String>>,
    ) {
        // Artifact mapped to existing versions. In turn each version maps to the actual JAR / AAR paths
        val artifacts = mutableMapOf<String, MutableMap<String, MutableSet<String>>>() // TODO: refactor
        val fullPaths = mutableSetOf<String>()

        cacheResources.forEach { res ->
            val fullArtifactId = "${res.pkg}:${res.artifact}"
            val versions = artifacts[fullArtifactId] ?: mutableMapOf()
            val paths = versions[res.version] ?: mutableSetOf()

            paths += res.fullPath
            versions[res.version] = paths
            artifacts[fullArtifactId] = versions
            fullPaths += res.fullPath
        }
        resolvedLibs.forEach { (artifact, version) ->
            if (!cachePaths.containsKey(artifact)) {
                val versions = artifacts[artifact]

                if (versions == null) {
                    Telemetry.err("No JARs / AARs found in Gradle cache for artifact: $artifact ($version)")
                } else if (version.isBlank()) {
                    val latestVersion = versions.keys.maxOrNull()
                    val paths = checkNotNull(versions[latestVersion]) { "No paths for version $version" }
                    cachePaths += artifact to paths
                } else {
                    var paths = versions[version]

                    if (paths == null) {
                        val latestVersion = versions.keys.maxOrNull()
                        val fallbackVersion = versions.keys.lastOrNull { it.isVersionGreaterOrEquals(version) } ?: latestVersion
                        checkNotNull(fallbackVersion) { "Fallback version is null for artifact $artifact:$version" }
                        paths = checkNotNull(versions[fallbackVersion]) { "No paths for version $version" }
                        cachePaths += artifact to paths
                    } else {
                        cachePaths += artifact to paths
                    }
                }
            }
        }
    }

    private fun isIgnoredModule(moduleName: String) = ignoredModules.contains(moduleName)

    private fun isIgnoredLib(artifact: String) = ignoredLibs.contains(artifact)

    private fun parseFilesDependency(modulePath: String, line: String): Dependency? {
        if (line.count { char -> char == '\'' } != 2) {
            error("[File dependency] Failed to parse line: $line")
        }
        val startIndex = line.indexOf('\'')
        val endIndex = line.lastIndexOf('\'')

        if (startIndex == -1 || endIndex == -1) {
            error("[File dependency] Failed to parse line: $line")
        }
        val filePath = line.substring(startIndex + 1, endIndex)
        return when {
            line.hasFilesImplementationPrefix() -> Dependency.Files(modulePath = modulePath, filePath = filePath, relation = Relation.IMPLEMENTATION)
            line.hasFilesApiPrefix() -> Dependency.Files(modulePath = modulePath, filePath = filePath, relation = Relation.API)
            else -> null
        }
    }

    private fun parseModuleDependency(line: String): Dependency? {
        val startIndex = line.indexOf("':")
        val endIndex = line.lastIndexOf("'")

        if (startIndex == -1 || endIndex == -1) {
            error("[Module dependency] Failed to parse line: $line")
        }
        val moduleName = line.substring(startIndex + 2, endIndex).replace(":", "/")
        return when {
            line.hasProjectImplementationPrefix() -> {
                Dependency.Project(moduleName = moduleName, relation = Relation.IMPLEMENTATION)
            }
            line.hasProjectDebugImplementationPrefix() -> {
                Dependency.Project(moduleName = moduleName, relation = Relation.DEBUG_IMPLEMENTATION)
            }
            line.hasProjectCompileOnlyPrefix() -> {
                Dependency.Project(moduleName = moduleName, relation = Relation.COMPILE_ONLY)
            }
            line.hasProjectApiPrefix() -> {
                Dependency.Project(moduleName = moduleName, relation = Relation.API)
            }
            line.hasProjectAndroidTestImplementationPrefix() -> {
                Dependency.Project(moduleName = moduleName, relation = Relation.ANDROID_TEST_IMPLEMENTATION)
            }
            line.hasProjectTestImplementationPrefix() -> {
                Dependency.Project(moduleName = moduleName, relation = Relation.TEST_IMPLEMENTATION)
            }
            else -> null
        }
    }

    private fun parseLibraryDependency(line: String): Dependency? {
        val relation = when {
            line.hasLibraryImplementationPrefix() -> Relation.IMPLEMENTATION
            line.hasLibraryApiPrefix() -> Relation.API
            line.hasLibraryAndroidTestImplementationPrefix() -> Relation.ANDROID_TEST_IMPLEMENTATION
            line.hasLibraryTestImplementationPrefix() -> Relation.TEST_IMPLEMENTATION
            else -> return null
        }
        val extractVersion = { line: String ->
            val lastColonIndex = line.lastIndexOf(":")
            line.substring(lastColonIndex + 1, line.length).run {
                split("'")
                    // TODO: refactor
                    .map { part ->
                        if (part.startsWith("\${") && part.endsWith("}")) {
                            part.substring(part.indexOf("{") + 1, part.indexOf("}"))
                        } else {
                            part
                        }
                    }
                    .map { part ->
                    part.replace("@aar", "") // TODO: refactor
                }.find(::isVersionOrPlaceholder) ?: error("[Library dependency] Failed to extract version: $line")
            }
        }
        val artifact: String
        var version: String? = null

        line.run {
            if (line.contains("group:") && contains("name:") && contains("version:")) {
                val parts = line.split(",")
                var group: String? = null
                var name: String? = null

                parts.forEach { part ->
                    when {
                        part.contains("group:") -> group = part.textInQuotes()
                        part.contains("name:") -> name = part.textInQuotes()
                        part.contains("version:") -> version = part.textInQuotes()
                    }
                }
                checkNotNull(group) { "[Library dependency] Failed to parse parameter 'group' in line: $line" }
                checkNotNull(name) { "[Library dependency] Failed to parse parameter 'name' in line: $line" }
                checkNotNull(version) { "[Library dependency] Failed to parse parameter 'version' in line: $line" }
                artifact = "$group:$name"

            } else if (line.contains("rootProject")) {
                val parts = line.split("rootProject")
                val left = parts[0]
                val right = parts[1]
                // TODO: improve and check for errors
                artifact = left.substring(left.indexOf("'") + 1, left.lastIndexOf(":"))

                version = when (parts.size) {
                    2 -> {
                        // TODO: improve and check for errors
                        val start = right.indexOf("'") + 1
                        right.substring(start, right.indexOf("'", start))
                    }
                    else -> {
                        "" // Don't parse compound artifact version, just use the latest one
                    }
                }
            } else {
                val startIndex = line.indexOf("'")
                val endIndex = line.lastIndexOf(":")

                if (startIndex == -1 || endIndex == -1) {
                    error("[Library dependency] Failed to parse line: $line")
                }
                artifact = substring(startIndex + 1, endIndex)
                version = extractVersion(line)
            }
        }
        checkNotNull(version) { "[Library dependency] Failed to parse parameter 'version' in line: $line" }
        // TODO: refactor
        return Dependency.Library(artifact = artifact, version = version!!, relation = relation)
    }

    private fun isVersionOrPlaceholder(part: String): Boolean {
        if (part.isBlank()) {
            return false
        }
        part.forEach { c ->
            if (c != '.' && c != '-' && c != '_' && !c.isLetterOrDigit()) { // TODO: improve
                return false
            }
        }
        return true
    }

    private fun isVersionResolved(version: String) = version[0].isDigit()

    private fun String.textInQuotes(): String {
        val startIndex = indexOf("'")
        val endIndex = lastIndexOf("'")
        when {
            startIndex == -1 -> error("No start quote for text: $this")
            endIndex == -1 -> error("No end quote for text: $this")
        }
        return substring(startIndex + 1, endIndex)
    }

    // TODO: need to optimize
    private fun String.collapse() = replace(" ", "").replace("(", "").trim()

    // Files
    private fun String.hasFilesImplementationPrefix() = collapse().startsWith("implementationfiles")

    private fun String.hasFilesApiPrefix() = collapse().startsWith("apifiles")

    private fun String.hasFileTreeImplementationPrefix() = collapse().startsWith("implementationfileTree")

    // Project
    private fun String.hasProjectImplementationPrefix() = collapse().startsWith("implementationproject")

    private fun String.hasProjectDebugImplementationPrefix() = collapse().startsWith("debugImplementationproject")

    private fun String.hasProjectApiPrefix() = collapse().startsWith("apiproject")

    private fun String.hasProjectCompileOnlyPrefix() = collapse().startsWith("compileOnlyproject")

    private fun String.hasProjectAndroidTestImplementationPrefix() = collapse().startsWith("androidTestImplementationproject")

    private fun String.hasProjectTestImplementationPrefix() = collapse().startsWith("testImplementationproject")

    // Library
    private fun String.hasLibraryImplementationPrefix() = collapse().startsWith("implementation")

    private fun String.hasLibraryApiPrefix() = collapse().startsWith("api")

    private fun String.hasLibraryAndroidTestImplementationPrefix() = collapse().startsWith("androidTestImplementation")

    private fun String.hasLibraryTestImplementationPrefix() = collapse().startsWith("testImplementation")

    private companion object {
        const val BUILD_GRADLE_FILE_NAME = "build.gradle"
        const val DEPENDENCIES_BLOCK_START = "dependencies"
        const val DEPENDENCIES_BLOCK_END = "}"
    }
}