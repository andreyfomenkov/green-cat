package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.data.PomDescriptor
import ru.fomenkov.plugin.repository.parser.PomFileParser
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import java.io.File
import java.lang.StringBuilder

class ArtifactDependencyResolver(private val pomFileParser: PomFileParser) {

    private val cacheDir = "/Users/andrey.fomenkov/.gradle/caches/modules-2/files-2.1" // TODO

    fun resolvePaths(groupId: String, artifactId: String, version: String): Map<PomDescriptor, String> {
        val output = mutableMapOf<PomDescriptor, String>() // POM -> jar or aar path
        resolve(groupId, artifactId, version, 0, output)
        return output
    }

    private fun resolve(groupId: String, artifactId: String, version: String, level: Int, output: MutableMap<PomDescriptor, String>) {
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
            val resources = exec("find ${versionDir.absolutePath}")
            val pomPaths = resources.filter { path -> path.endsWith(".pom") }
            val archivePaths = resources
                .filter { path -> path.endsWith(".jar") || path.endsWith(".aar") }
                .filterNot { path -> path.endsWith("-sources.jar") }
                .filterNot { path -> path.endsWith("-javadoc.jar") }
            val pom = when (pomPaths.size) {
                0 -> return
                1 -> pomFileParser.parse(pomPaths.first())
                else -> error("Multiple POM files found at ${versionDir.absolutePath}")
            }
            val archive = when (archivePaths.size) {
                0 -> null
                1 -> archivePaths.first()
                else -> error("Multiple archives files found at ${versionDir.absolutePath}")
            }
            if (archive == null) {
                Telemetry.err("No archives found at ${versionDir.absolutePath}")
            } else {
                output += descriptor to archive
            }
            pom.dependencies.forEach { dep ->
                if (dep.scope.isTransitive()) {
                    val artifactDir = getArtifactDir(dep.descriptor.groupId, dep.descriptor.artifactId)

                    if (File(artifactDir).exists()) {
                        val allVersions = File(artifactDir).list { file, _ -> file.isDirectory } ?: emptyArray()
                        val latestVersion = getLatestVersion(artifactDir, allVersions)
                        resolve(dep.descriptor.groupId, dep.descriptor.artifactId, latestVersion, level + 1, output)
                    } else {
                        Telemetry.err("No artifact directory: $artifactDir")
                    }
                }
            }
            // TODO: jar, aar
        } else {
            error("No artifact directory: ${versionDir.absolutePath}")
        }
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
                builder.append("  ")
            }
            builder.toString()
        } else {
            ""
        }
    }
}