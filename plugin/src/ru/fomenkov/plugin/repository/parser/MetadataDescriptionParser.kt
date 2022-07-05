package ru.fomenkov.plugin.repository.parser

import java.io.File

class MetadataDescriptionParser {

    /**
     * @return artifact to transitive status (true is transitive)
     */
    fun parse(path: String): Map<Artifact, Boolean> {
        val result = mutableMapOf<Artifact, Boolean>()
        val file = File(path)

        if (!file.exists() || !file.isFile) {
            error("Metadata descriptor file doesn't exist: $path")
        }
        val lines = file.readText()
            .split(DELIMITER)
            .map(String::trim)

        lines.forEachIndexed { index, line ->
            val isVersion = isValidVersion(line)

            if (isVersion && index > 1) {
                val groupId = lines[index - 2]
                val artifactId = lines[index - 1]
                val version = lines[index]

                if (isValidGroupIdOrArtifact(groupId) && isValidGroupIdOrArtifact(artifactId)) {
                    val artifact = Artifact(groupId, artifactId, version)

                    if (artifact in result) {
                        result[artifact] = true // Transitive dependencies can be found twice in a binary file?
                    } else {
                        result += artifact to false
                    }
                }
            }
        }
        return result
    }

    private fun isValidGroupIdOrArtifact(text: String): Boolean {
        if (text.isBlank()) {
            return false
        }
        // Assuming group ID or artifact starts with letter
        if (!text.first().isLetter()) {
            return false
        }
        text.forEach { char ->
            if (!char.isLetterOrDigit() && char !in ALLOWED_CHARS) {
                return false
            }
        }
        return true
    }

    private fun isValidVersion(text: String): Boolean {
        if (text.isBlank()) {
            return false
        }
        // Assuming version starts with digit
        if (!text.first().isDigit()) {
            return false
        }
        text.forEach { char ->
            if (!char.isLetterOrDigit() && char !in ALLOWED_CHARS) {
                return false
            }
        }
        return true
    }

    data class Artifact(val groupId: String, val artifactId: String, val version: String)

    private companion object {
        val DELIMITER = Char(65533)
        val ALLOWED_CHARS = setOf('.', '-')
    }
}