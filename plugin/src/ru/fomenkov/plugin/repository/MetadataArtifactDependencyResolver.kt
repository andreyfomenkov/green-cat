package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.parser.MetadataDescriptionParser
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.noTilda
import java.io.File
import java.lang.StringBuilder

class MetadataArtifactDependencyResolver(
    private val jetifiedJarRepository: JetifiedJarRepository,
    private val parser: MetadataDescriptionParser,
) {

    private val versionPaths = mutableMapOf<String, Set<String>>() // groupId:artifact:version -> resource paths
    private val cacheDir = "~/.gradle/caches/modules-2".noTilda()
    private val filesDir: String
    private val metadataDir: String

    init {
        if (!File(cacheDir).exists()) {
            error("Gradle cache path doesn't exist: $cacheDir")
        }
        filesDir = getFilesDirectory()
        metadataDir = getMetadataDirectory()
        jetifiedJarRepository.scan()
    }

    fun resolvePaths(groupId: String, artifactId: String, version: String): Set<String> {
        val artifactKey = composeKey(groupId, artifactId, version)
        val cachedPaths = versionPaths[artifactKey]

        if (cachedPaths != null && cachedPaths.isNotEmpty()) {
            return cachedPaths
        }
        val artifacts = mutableSetOf<MetadataDescriptionParser.Artifact>()
        val paths = mutableSetOf<String>()
        resolvePaths(groupId, artifactId, version, false, artifacts, level = 0)

        artifacts.forEach { artifact ->
            val jetifiedPaths = jetifiedJarRepository.getArtifactPaths(artifact.artifactId, artifact.version)
            paths += jetifiedPaths
                .excludeSourceAndJavadocResources()
                .ifEmpty { getPathsFromSupportCache(groupId, artifactId, version) }
        }
        versionPaths[artifactKey] = paths
        return paths
    }

    private fun resolvePaths(
        groupId: String,
        artifactId: String,
        version: String,
        strictVersion: Boolean, // TODO: research about version constraint
        output: MutableSet<MetadataDescriptionParser.Artifact>,
        level: Int,
    ) {
        if (!getMetadataArtifactDir(groupId, artifactId).exists()) {
            return
        }
        val currentVersion = if (strictVersion) {
            version
        } else {
            getLatestMetadataArtifactVersion(groupId, artifactId)
        }
        if (currentVersion == null) {
            return
        }
        val metadataDir = getMetadataArtifactVersionDir(groupId, artifactId, currentVersion)
        val parentArtifact = MetadataDescriptionParser.Artifact(groupId, artifactId, currentVersion)

        if (!metadataDir.exists()) {
            error("No metadata directory: ${metadataDir.absolutePath}")
        }
        output += parentArtifact
        val hashDirs = metadataDir.listFiles { file, _ -> file.isDirectory }

        if (hashDirs.isNullOrEmpty()) {
            error("No hash directories found at ${metadataDir.absolutePath}")
        }
        val descriptor = File("${hashDirs.first()}/descriptor.bin")

        if (!descriptor.exists()) {
            error("Metadata descriptor doesn't exist: ${descriptor.absolutePath}")
        }
        val dependencies = parser.parse(descriptor.absolutePath)

        dependencies.forEach { (artifact, isTransitive) ->
            if (artifact !in output) {
                output += artifact

                if (isTransitive) {
                    resolvePaths(artifact.groupId, artifact.artifactId, artifact.version, false, output, level + 1)
                }
            }
        }
    }

    // TODO: research for version constraint
    private fun getPathsFromSupportCache(groupId: String, artifactId: String, version: String): Set<String> {
        val artifactKey = composeKey(groupId, artifactId, version)
        val cachedPaths = versionPaths[artifactKey]

        if (cachedPaths != null && cachedPaths.isNotEmpty()) {
            return cachedPaths
        }
        val paths = mutableSetOf<String>()
        var versionDir = getSupportArtifactVersionDir(groupId, artifactId, version)

        if (!versionDir.exists()) {
            val artifactDir = getSupportArtifactDir(groupId, artifactId)

            if (!artifactDir.exists()) {
                error("No artifact directory: ${artifactDir.absolutePath}")
            }
            val latestVersion = getLatestSupportArtifactVersion(groupId, artifactId)

            if (latestVersion == null) {
                error("No versions for artifact: ${artifactDir.absolutePath}")
            } else {
                versionDir = File(artifactDir, latestVersion)
            }
        }
        paths += exec("find ${versionDir.absolutePath}")
            .filter { path -> path.endsWith(".jar") || path.endsWith(".aar") }
            .excludeSourceAndJavadocResources()

        versionPaths[artifactKey] = paths
        return paths
    }

    private fun composeKey(groupId: String, artifactId: String, version: String) = "$groupId:$artifactId:$version"

    private fun Collection<String>.excludeSourceAndJavadocResources() = this
        .filterNot { path -> path.endsWith("-sources.jar") }
        .filterNot { path -> path.endsWith("-javadoc.jar") }

    private fun getLatestSupportArtifactVersion(groupId: String, artifactId: String): String? {
        val artifactDir = File("$filesDir/$groupId/$artifactId")

        if (!artifactDir.exists()) {
            error("No artifact directory: ${artifactDir.absolutePath}")
        }
        val versionDirs = artifactDir.listFiles { dir, name -> dir.isDirectory && name.first().isDigit() } ?: emptyArray()

        if (versionDirs.isEmpty()) {
            Telemetry.err("No version directories at ${artifactDir.absolutePath}")
            return null
        }
        return versionDirs.maxOf { dir -> dir.name }
    }

    private fun getLatestMetadataArtifactVersion(groupId: String, artifactId: String): String? {
        val artifactDir = File("$metadataDir/descriptors/$groupId/$artifactId")

        if (!artifactDir.exists()) {
            error("No artifact directory: ${artifactDir.absolutePath}")
        }
        val versionDirs = artifactDir.listFiles { dir, name -> dir.isDirectory && name.first().isDigit() } ?: emptyArray()

        if (versionDirs.isEmpty()) {
            Telemetry.err("No version directories at ${artifactDir.absolutePath}")
            return null
        }
        return versionDirs.maxOf { dir -> dir.name }
    }

    private fun getSupportArtifactDir(groupId: String, artifactId: String) =
        File("$filesDir/$groupId/$artifactId")

    private fun getSupportArtifactVersionDir(groupId: String, artifactId: String, version: String) =
        File("$filesDir/$groupId/$artifactId/$version")

    private fun getMetadataArtifactDir(groupId: String, artifactId: String) =
        File("$metadataDir/descriptors/$groupId/$artifactId")

    private fun getMetadataArtifactVersionDir(groupId: String, artifactId: String, version: String) =
        File("$metadataDir/descriptors/$groupId/$artifactId/$version")

    private fun getFilesDirectory(): String {
        val dirs = File(cacheDir).listFiles { file, name -> file.isDirectory && name.startsWith("files-") }

        if (dirs == null) {
            error("No files-* directory found at $cacheDir")
        } else if (dirs.size > 1) {
            error("Multiple files-* directories found at $cacheDir")
        }
        return dirs.first().absolutePath
    }

    private fun getMetadataDirectory(): String {
        val dirs = File(cacheDir).listFiles { file, name -> file.isDirectory && name.startsWith("metadata-") }

        if (dirs == null) {
            error("No metadata-* directory found at $cacheDir")
        } else if (dirs.size > 1) {
            error("Multiple metadata-* directories found at $cacheDir")
        }
        return dirs.first().absolutePath
    }

    private fun spaces(level: Int) = if (level > 0) {
        val builder = StringBuilder()

        for (i in 0 until level) {
            builder.append("  ")
        }
        builder.toString()
    } else {
        ""
    }
}