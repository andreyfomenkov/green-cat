package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.data.PomDescriptor
import ru.fomenkov.plugin.repository.parser.PomFileParser
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.noTilda
import java.io.File
import java.lang.StringBuilder

class ArtifactDependencyResolver(
    private val jetifiedJarRepository: JetifiedJarRepository,
    private val pomFileParser: PomFileParser,
) {
    private val cacheDir = "~/.gradle/caches/modules-2/files-2.1".noTilda() // TODO: search between modules-X
    private val output = mutableMapOf<PomDescriptor, Set<String>>() // POM -> JAR/AAR paths

    init {
        if (!File(cacheDir).exists()) {
            error("Gradle cache path doesn't exist: $cacheDir")
        }
        jetifiedJarRepository.scan()
    }

    fun resolvePaths(groupId: String, artifactId: String, version: String): Set<String> {
        try {
            resolve(groupId, artifactId, version, 0)
            output.forEach { (desc, _) ->
                val paths = jetifiedJarRepository.getArtifactPaths(desc.artifactId, desc.version)

                if (paths.isNotEmpty()) {
                    output[desc] = paths
                }
            }
        } catch (error: Throwable) {
            Telemetry.verboseErr("Failed to resolve paths (message = ${error.localizedMessage})")
        }
        val result = mutableSetOf<String>()
        output.values.forEach { paths -> result += paths }
        return result
    }

    private fun resolve(groupId: String, artifactId: String, version: String, level: Int) {
        val descriptor = PomDescriptor(groupId, artifactId, version)
        if (descriptor in output) {
            return
        }
        var versionDir = File(getVersionDir(groupId, artifactId, version))

        if (!versionDir.exists()) {
            val artifactDir = getArtifactDir(groupId, artifactId)
            val allVersions = File(artifactDir).list { file, _ -> file.isDirectory } ?: emptyArray()
            val latestVersion = getLatestVersion(artifactDir, allVersions)
            versionDir = File(getVersionDir(groupId, artifactId, latestVersion))
        }
        if (versionDir.exists()) {
            val resources = listVersionDirResources(versionDir.absolutePath)
            val pomPaths = resources.filter { path -> path.endsWith(".pom") }
            val archivePaths = resources
                .filter { path -> path.endsWith(".jar") || path.endsWith(".aar") }
                .filterNot { path -> path.endsWith("-sources.jar") }
                .filterNot { path -> path.endsWith("-javadoc.jar") }
            val pom = when (pomPaths.size) {
                0 -> null // No POM file can be found
                1 -> pomFileParser.parse(pomPaths.first())
                else -> error("Multiple POM files found at ${versionDir.absolutePath}")
            }
            val archives = when (archivePaths.size) {
                0 -> null
                else -> archivePaths.toSet()
            }
            if (archives == null) {
                Telemetry.verboseErr("No archives found at ${versionDir.absolutePath}")
            } else {
                output += descriptor to archives
            }
            pom?.dependencies?.forEach { dep ->
                if (dep.scope.isTransitive()) {
                    val artifactDir = getArtifactDir(dep.descriptor.groupId, dep.descriptor.artifactId)

                    if (File(artifactDir).exists()) {
                        val allVersions = File(artifactDir).list { file, _ -> file.isDirectory } ?: emptyArray()
                        val latestVersion = getLatestVersion(artifactDir, allVersions)
                        resolve(dep.descriptor.groupId, dep.descriptor.artifactId, latestVersion, level + 1)
                    } else {
                        Telemetry.verboseErr("No artifact directory: $artifactDir")
                    }
                }
            }
        } else {
            error("No artifact directory: ${versionDir.absolutePath}")
        }
    }

    private fun listVersionDirResources(path: String): List<String> {
        val resources = mutableListOf<String>()
        val hashDirs = File(path).list { file, _ -> file.isDirectory } ?: emptyArray()
        hashDirs.forEach { hashDir ->
            resources += File("$path/$hashDir")
                .listFiles()
                ?.map { file -> file.absolutePath } ?: emptyList()
        }
        return resources
    }

    private fun getVersionDir(groupId: String, artifactId: String, version: String) = "$cacheDir/$groupId/$artifactId/$version"

    private fun getArtifactDir(groupId: String, artifactId: String) = "$cacheDir/$groupId/$artifactId"

    private fun getLatestVersion(artifactDir: String, versions: Array<String>) =
        when {
            versions.isEmpty() -> error("No available versions for artifact: $artifactDir")
            else -> versions.maxOrNull()!!
        }

    private fun spaces(level: Int): String {
        return if (level > 0) {
            val builder = StringBuilder()

            for (i in 0 until level) {
                builder.append("-")
            }
            builder.toString()
        } else {
            ""
        }
    }
}