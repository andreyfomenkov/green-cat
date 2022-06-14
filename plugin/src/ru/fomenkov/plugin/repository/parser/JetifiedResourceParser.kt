package ru.fomenkov.plugin.repository.parser

import ru.fomenkov.plugin.repository.JetifiedJarRepository

class JetifiedResourceParser {

    fun parse(path: String): JetifiedJarRepository.Entry {
        val pathParts = path.trim().split("/")

        val description = if (pathParts[pathParts.size - 2] == "jars") {
            pathParts[pathParts.size - 3]
        } else {
            val last = pathParts.last()

            if (last.endsWith(".jar")) {
                last.substring(0, last.length - 4)
            } else if (last.endsWith(".aar")) {
                last.substring(0, last.length - 4)
            } else {
                throw IllegalArgumentException("Unknown extension: $path")
            }
        }
        val artifactParts = description.split("-").toMutableList()

        if (artifactParts.first() == "jetified") {
            artifactParts.removeFirst()
        }
        if (artifactParts.last() == "api") {
            artifactParts.removeLast()
        }
        var versionStartIndex = artifactParts.indexOfFirst { part ->
            part[0].isDigit() && part.contains(".")
        }
        if (versionStartIndex == -1) {
            // Can be a strange artifact version (see unit test for jetified-cameraview-5b2f0fff93.aar)
            versionStartIndex = artifactParts.size - 1
        }
        val artifactId = artifactParts
            .subList(0, versionStartIndex)
            .joinToString(separator = "-")

        val version = artifactParts
            .subList(versionStartIndex, artifactParts.size)
            .joinToString(separator = "-")

        return JetifiedJarRepository.Entry(artifactId, version)
    }
}