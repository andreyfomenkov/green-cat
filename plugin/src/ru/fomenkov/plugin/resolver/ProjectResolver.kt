package ru.fomenkov.plugin.resolver

import ru.fomenkov.plugin.repository.data.PomDependency
import ru.fomenkov.plugin.repository.data.PomDependencyScope
import ru.fomenkov.plugin.task.resolve.GradleCacheItem
import ru.fomenkov.plugin.util.*
import java.io.File
import java.io.FileFilter

class ProjectResolver(
    private val propertiesFileName: String,
    private val settingsFileName: String,
    private val ignoredModules: Set<String> = emptySet(),
    private val ignoredLibs: Set<String> = emptySet(),
) {

    private val unresolvedArtifacts = mutableSetOf<String>()

    /**
     * Parse Gradle properties file with placeholder to version values
     */
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

    // Sometimes property value: 'artifact = version' can be found in build.gradle file
    // We should add it to main properties collection (properties mutable map)
    fun parseModuleBuildGradleFile(
        modulePath: String,
        properties: MutableMap<String, String>,
    ): Set<Dependency> {
        Telemetry.verboseLog("Parsing $modulePath/build.gradle")
        val deps = mutableSetOf<Dependency>()
        val path = "$modulePath/$BUILD_GRADLE_FILE_NAME"
        val variables = mutableMapOf<String, String>()

        File(path).readLines()
            .map { line -> line.trim().replace("\"", "'") }
            // TODO: refactor
            .filterNot { line -> line.isBlank() || line.startsWith("#") || line.startsWith("//") || line.startsWith("implementationClass") }
            .forEach { line ->
                // Property can be found in build.gradle file -> add to main properties collection
                if (line.startsWith("project.ext")) {
                    val parts = line.split("=")

                    if (parts.size != 2) {
                        error("[$path] Failed to parse property: $line")
                    }
                    val artifact = parts[0].trim()
                    val version = parts[1].replace("\"", "").replace("'", "").trim() // TODO: refactor
                    properties += artifact to version
                } else if (line.isRootProjectPropertyVariableDefinition()) { // When library version in a separate variable
                    variables += line.extractRootProjectPropertyVariable()
                }
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
                        parseLibraryDependency(variables, line)
                    }
                    else -> null
                }
                if (dependency != null) {
                    deps += dependency
                } else {
                    Telemetry.verboseErr("[$path] Skipping line: $line")
                }
            }
        return deps
    }

    private fun String.isRootProjectPropertyVariableDefinition() = this.run {
        startsWith("def ") && contains("=") && contains("rootProject") && contains("property")
    }

    private fun String.extractRootProjectPropertyVariable() = this.run {
        val nameIndexStart = indexOf("def ")
        val nameIndexEnd = indexOf("=")
        check(nameIndexStart in 0 until nameIndexEnd) { "Failed to parse variable name" }
        val name = substring(nameIndexStart + 3, nameIndexEnd).trim()

        val valueIndexStart = indexOf("'")
        check(valueIndexStart > 0) { "Failed to parse variable value (start index)" }
        val valueIndexEnd = indexOf("'", valueIndexStart + 1)
        check(valueIndexEnd > 0) { "Failed to parse variable value (end index)" }
        val value = substring(valueIndexStart + 1, valueIndexEnd).trim()

        name to value
    }

    /**
     * @param path Gradle cache root path
     * @return all cache resources (JAR, AAR archives and POM files)
     */
    fun findAllResourcesInGradleCache(path: String): Set<GradleCacheItem> {
        Telemetry.verboseLog("List all JAR, AAR and POM files in Gradle cache")
        val fullPath = path.replace("~", HOME_DIR)
        val items = mutableSetOf<GradleCacheItem>()

        File(fullPath).files { packageDir ->
            packageDir.files { artifactDir ->
                artifactDir.files { versionDir ->
                    versionDir.files { hashDir ->
                        hashDir.files { resourceFile ->
                            // TODO: filter -sources.* and -javadoc.*?
                            if (resourceFile.extension == "jar" || resourceFile.extension == "aar") {
                                items += GradleCacheItem.Archive(
                                    pkg = packageDir.name,
                                    artifact = artifactDir.name,
                                    version = versionDir.name,
                                    resource = resourceFile.name,
                                    fullPath = resourceFile.path,
                                )
                            } else if (resourceFile.extension == "pom") {
//                                items += parsePomFile(
//                                    pkg = packageDir.name,
//                                    artifact = artifactDir.name,
//                                    version = versionDir.name,
//                                    pomFile = resourceFile,
//                                )
                            }
                        }
                    }
                }
            }
        }
        return items
    }

    /**
     * @param path Gradle cache root path
     * @return jetified artifact names + version mapped to their paths in cache (classes.jar and /res if any)
     */
    fun findAllJetifiedJarsInGradleCache(path: String): Map<String, Set<String>> {
        Telemetry.verboseLog("List all jetified JARs")
        val fullPath = path.replace("~", HOME_DIR)
        val paths = mutableMapOf<String, Set<String>>()
        val directoryFilter = FileFilter { it.isDirectory }

        File(fullPath).files { hashDir ->
            if (hashDir.isDirectory) {
                hashDir.files(filter = directoryFilter) { innerDir ->
                    if (innerDir.name == "transformed") {
                        innerDir.files(filter = directoryFilter) { file ->
                            val classesJarPath = "${file.absolutePath}/jars/classes.jar"
                            val resDirPath = "${file.absolutePath}/res"
                            val resources = mutableSetOf<String>()

                            if (File(classesJarPath).exists()) {
                                resources += classesJarPath
                            }
                            if (File(resDirPath).exists()) {
                                resources += resDirPath
                            }
                            if (resources.isNotEmpty()) {
                                val name = file.name.replace("jetified-", "").trim()
                                paths[name] = resources
                            }
                        }
                    }
                }
            }
        }
        return paths
    }

    private fun parsePomFile(pkg: String, artifact: String, version: String, pomFile: File): GradleCacheItem.Pom {
        val lines = pomFile.readLines()
        val deps = mutableSetOf<PomDependency>()
        var insideBlock = false
        var depGroupId: String? = null
        var depArtifactId: String? = null
        var depVersion = "" // Default value
        var scope = PomDependencyScope.COMPILE // Default value

        if (lines.isEmpty()) {
            error("POM file is empty: ${pomFile.absolutePath}")
        }
        val getParameterValue = { line: String ->
            val start = line.indexOf(">")
            val end = line.indexOf("</")

            if (start == -1 || end == -1) { // TODO: refactor
                error("Failed to parse parameter: $line")
            }
            line.substring(start + 1, end)
        }
        lines
            .map { line -> line.trim() }
            .forEach { line ->
                when {
                    line.startsWith("<dependency>") -> {
                        if (insideBlock) {
                            error("Unexpected line: $line")
                        }
                        insideBlock = true
                    }
                    line.startsWith("</dependency>") -> {
                        if (!insideBlock) {
                            error("Unexpected line: $line")
                        }
//                        deps += PomDependency(
//                            groupId = checkNotNull(depGroupId) { "No parameter groupId: ${pomFile.absolutePath}" },
//                            artifactId = checkNotNull(depArtifactId) { "No parameter artifactId: ${pomFile.absolutePath}" },
//                            version = depVersion,
//                            scope = scope,
//                        )
                        insideBlock = false
                        depGroupId = null
                        depArtifactId = null
                        depVersion = "" // Default value
                        scope = PomDependencyScope.COMPILE // Default value
                    }
                    insideBlock && line.startsWith("<groupId>") -> {
                        depGroupId = getParameterValue(line)
                    }
                    insideBlock && line.startsWith("<artifactId>") -> {
                        depArtifactId = getParameterValue(line)
                    }
                    insideBlock && line.startsWith("<version>") -> {
                        depVersion = getParameterValue(line)
                    }
                    insideBlock && line.startsWith("<scope>") -> {
                        val value = getParameterValue(line).trim().uppercase()
                        scope = PomDependencyScope.valueOf(value)
                    }
                    insideBlock -> {
                        Telemetry.verboseErr("[POM] Unrecognized parameter: $line")
                    }
                }
            }
//        return GradleCacheItem.Pom(pkg = pkg, artifact = artifact, version = version, dependencies = deps)
        return GradleCacheItem.Pom(pkg = pkg, artifact = artifact, version = version, dependencies = emptySet())
    }

    private fun File.files(filter: FileFilter? = null, action: (File) -> Unit) = checkNotNull(listFiles(filter)) {
        "Failed to list files for directory: $path"
    }.forEach(action)

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
                            Telemetry.err("[$modulePath] No placeholder version: $placeholderOrVersion")
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
     * @param cacheItems all JAR, AAR or POM resources in Gradle cache
     * @param cachePaths output map for artifacts to their archive paths in Gradle cache, including transitive
     */
    fun getArtifactArchivePaths(
        resolvedLibs: Map<String, String>,
        cacheItems: Set<GradleCacheItem>,
        cachePaths: MutableMap<String, Set<String>>,
    ) {
        // Artifact mapped to existing versions. In turn each version maps to the actual JAR / AAR paths
        val artifacts = mutableMapOf<String, MutableMap<String, MutableSet<String>>>() // TODO: refactor

        cacheItems
            .asSequence()
            .filterIsInstance<GradleCacheItem.Archive>()
            .forEach { archive ->
                val fullArtifactId = "${archive.pkg}:${archive.artifact}"
                val versions = artifacts[fullArtifactId] ?: mutableMapOf()
                val paths = versions[archive.version] ?: mutableSetOf()

                paths += archive.fullPath
                versions[archive.version] = paths
                artifacts[fullArtifactId] = versions
            }
        resolvedLibs.forEach { (artifact, version) ->
            if (!cachePaths.containsKey(artifact)) {
                val versions = artifacts[artifact]

                if (versions == null) {
                    val entry = "$artifact ($version)"

                    if (!unresolvedArtifacts.contains(entry)) {
                        unresolvedArtifacts += entry
                        Telemetry.err("No JARs / AARs found in Gradle cache for artifact: $artifact ($version)")
                    }
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
        resolvedLibs.forEach { (artifact, version) -> // TODO: optimize
            val pom = cacheItems.asSequence()
                .filterIsInstance<GradleCacheItem.Pom>()
                .find { item -> "${item.pkg}:${item.artifact}" == artifact && item.version == version } // TODO: refactor

            pom?.dependencies?.forEach { dep ->
                val versions = artifacts[dep.groupId + ":" + dep.artifactId]

                if (versions != null) {
                    val transitive = versions[dep.version] ?: mutableSetOf()
                    val existing = cachePaths[artifact] ?: emptySet() // TODO: refactor
                    cachePaths[artifact] = existing + transitive
                }
            }
        }
    }

    /**
     * Find all module dependencies including transitive
     *
     * @param modulePath target module path
     * @param modules dependencies for all project modules
     * @param moduleNameToPath map for module path by name
     * @return a set of all dependencies for the target module, transitive are flattened
     */
    fun getAllModuleDependencies(
        modulePath: String,
        modules: Map<String, Set<Dependency>>,
        moduleNameToPath: Map<String, String>, // TODO: refactor
    ): Set<Dependency> {
        val getModulePathByName = { name: String ->
            val path = moduleNameToPath[name]
            if (path == null) {
                // May occur for dependencies inside try-catch block
                Telemetry.verboseErr("No module path for name: $name")
            }
            path
        }
        val getProjectDeps = { path: String ->
            checkNotNull(modules[path]) { "Module path $path not found" }
        }
        val moduleDeps = getProjectDeps(modulePath).toMutableSet()
        val tempDeps = mutableSetOf<Dependency>()
        val resolvedModulePaths = mutableSetOf<String>()
        var hasUnresolvedProjects = true

        while (hasUnresolvedProjects) {
            moduleDeps
                .filterNot { dep -> dep is Dependency.Project && isIgnoredModule(moduleName = dep.moduleName) }
                .filterNot { dep -> dep is Dependency.Library && isIgnoredLib(artifact = dep.artifact) }
                .forEach { dep ->
                    when (dep) {
                        is Dependency.Project -> {
                            val path = getModulePathByName(dep.moduleName)

                            if (path != null) {
                                tempDeps += dep.copy(relation = Relation.IMPLEMENTATION)

                                if (!resolvedModulePaths.contains(path)) {
                                    resolvedModulePaths += path
                                    tempDeps += getProjectDeps(path).filter { project -> project.isTransitive() }
                                }
                            }
                        }
                        is Dependency.Library -> {
                            tempDeps += dep.copy(relation = Relation.IMPLEMENTATION) // TODO: refactor
                        }
                        is Dependency.Files -> {
                            tempDeps += dep.copy(relation = Relation.IMPLEMENTATION)
                        }
                    }
                }
            moduleDeps.clear()
            moduleDeps += tempDeps
            tempDeps.clear()
            hasUnresolvedProjects = moduleDeps.find { project -> project.isTransitive() } != null
        }
        return moduleDeps
    }

    /**
     * Get compilation order for modules. Modules with the same order value can be compiled concurrently
     *
     * @param deps module paths involved into compilation mapped to their module dependencies (including transitive)
     * @return compilation order mapped to a set of module paths
     */
    fun getModuleCompilationOrder(deps: Map<String, Set<String>>): Map<Int, Set<String>> {
        val allPaths = deps.keys.toMutableSet()
        val hasChildInAllPaths = { path: String ->
            val children = checkNotNull(deps[path]) { "Unknown module path: $path" }
            (allPaths - children).size < allPaths.size
        }
        var order = 0
        val pathOrders = mutableMapOf<Int, Set<String>>()

        while (allPaths.isNotEmpty()) {
            val nextOrderModules = allPaths.filterNot { path -> hasChildInAllPaths(path) }.toSet()
            pathOrders += order to nextOrderModules
            allPaths -= nextOrderModules
            order++
        }
        return pathOrders
    }

    /**
     * Build classpath for the specified dependencies
     *
     * @param deps dependencies to build classpath for
     * @param cachePaths artifacts mapped to their resource paths in Gradle cache
     */
    fun buildClasspath(
        androidSdkPath: String,
        deps: Set<Dependency>,
        cachePaths: MutableMap<String, Set<String>>,
        moduleNameToPathMap: Map<String, String>, // TODO: refactor
    ): Set<String> {
        val classpath = mutableSetOf<String>()
        deps.forEach { dep ->
            when (dep) {
                is Dependency.Project -> {
                    val modulePath = checkNotNull(moduleNameToPathMap[dep.moduleName]) {
                        "No path for module: ${dep.moduleName}"
                    }
                    "$CURRENT_DIR/$modulePath".let { dir ->
                        classpath += "$dir/build/intermediates/javac/debug/classes"
                        classpath += "$dir/build/intermediates/javac/debugAndroidTest/classes"

                        // TODO: refactor. Find the exact 'compile_*_r_class_jar' directory
                        classpath += "$dir/build/intermediates/compile_r_class_jar/debug/R.jar"
                        classpath += "$dir/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar"

                        classpath += "$dir/build/tmp/kotlin-classes/debug"
                        classpath += "$dir/build/tmp/kapt3/classes/debug"
                        classpath += "$dir/build/generated/res/resValues/debug"
                        classpath += "$dir/build/generated/res/rs/debug"
                        classpath += "$dir/build/generated/crashlytics/res/debug"
                        classpath += "$dir/build/generated/res/google-services/debug"
                    }
                }
                is Dependency.Library -> {
                    val paths = cachePaths[dep.artifact]

                    if (paths == null) {
                        // TODO: looks like firebase-bom has no JAR or AAR resources
                        // TODO: it specifies version for other Firebase artifacts?
                        if (!dep.artifact.endsWith("firebase-bom")) {
                            error("No paths for artifact: ${dep.artifact}")
                        }
                    } else {
                        classpath += paths
                    }
                }
                is Dependency.Files -> {
                    classpath += "$CURRENT_DIR/${dep.modulePath}/${dep.filePath}"
                }
            }
        }
        if (!File(androidSdkPath).exists()) {
            error("Invalid Android SDK path: $androidSdkPath")
        }
        val platforms = File("$androidSdkPath/platforms").list()?.filter { path ->
            path.startsWith("android-")
        }
        when {
            platforms.isNullOrEmpty() -> {
                error("No platforms installed for Android SDK: ${androidSdkPath}/platform")
            }
            else -> {
                val platform = platforms.first() // TODO: choose the exact platform
                classpath += "$androidSdkPath/platforms/$platform/android.jar"
                classpath += "$androidSdkPath/platforms/$platform/data/res"
            }
        }
        // TODO: need JDK?
        return classpath
            .filter { path ->
                File(path).exists().also { exists ->
                    if (!exists) {
                        Telemetry.verboseErr("[Classpath] Path doesn't exist: $path")
                    }
                }
            }
            .toSet()
    }

    fun isIgnoredModule(moduleName: String) = ignoredModules.contains(moduleName)

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

    private fun parseLibraryDependency(variables: Map<String, String>, line: String): Dependency? {
        val relation = when {
            line.hasLibraryImplementationPrefix() -> Relation.IMPLEMENTATION
            line.hasLibraryApiPrefix() -> Relation.API
            line.hasLibraryAndroidTestImplementationPrefix() -> Relation.ANDROID_TEST_IMPLEMENTATION
            line.hasLibraryTestImplementationPrefix() -> Relation.TEST_IMPLEMENTATION
            else -> return null
        }
        val extractVersion = { arg: String ->
            val lastColonIndex = arg.lastIndexOf(":")

            fun extract(arg: String) = arg.substring(lastColonIndex + 1, arg.length)
                .split("'") // TODO: refactor
                .map { part ->
                    if (part.startsWith("\${") && part.endsWith("}")) {
                        part.substring(part.indexOf("{") + 1, part.indexOf("}"))
                    } else {
                        part
                    }
                }
                .map { part -> part.replace("@aar", "") } // TODO: refactor
                .find { part -> isVersionOrPlaceholder(part) }

            var result = extract(arg)

            if (result == null) {
                variables.forEach { (name, value) ->
                    if ("$$name" in arg) {
                        result = extract(arg.replace("$$name", value))

                        if (result != null) {
                            return@forEach
                        }
                    }
                }
            }
            result ?: error("[Library dependency] Failed to extract version: $arg")
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
            } else if (line.count { c -> c == ':' } == 1) {
                artifact = line.textInQuotes()
                version = ""
            } else {
                var cropped = line

                if (cropped.contains(":all'")) {
                    cropped = cropped.replace(":all", "") // TODO: refactor
                }
                val startIndex = cropped.indexOf("'")
                val endIndex = cropped.lastIndexOf(":")

                if (startIndex == -1 || endIndex == -1) {
                    error("[Library dependency] Failed to parse line: $line")
                }
                artifact = substring(startIndex + 1, endIndex)
                version = extractVersion(cropped)
            }
        }
        return Dependency.Library(
            artifact = artifact,
            version = checkNotNull(version) { "[Library dependency] Failed to parse parameter 'version' in line: $line" },
            relation = relation,
        )
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

    private fun String.hasLibraryApiPrefix() = collapse().startsWith("api") && !collapse().startsWith("apiLevel")

    private fun String.hasLibraryAndroidTestImplementationPrefix() = collapse().startsWith("androidTestImplementation")

    private fun String.hasLibraryTestImplementationPrefix() = collapse().startsWith("testImplementation")

    private companion object {
        const val BUILD_GRADLE_FILE_NAME = "build.gradle"
    }
}