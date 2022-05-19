package ru.fomenkov.plugin.repository.parser

import ru.fomenkov.plugin.repository.data.Pom
import ru.fomenkov.plugin.repository.data.PomDependency
import ru.fomenkov.plugin.repository.data.PomDependencyScope
import ru.fomenkov.plugin.repository.data.PomDescriptor
import java.io.File

class PomFileParser {

    fun parse(path: String): Pom {
        val lines = File(path).readLines()
        val parts = path.split("/")
        val count = parts.size
        val groupId = parts[count - 5]
        val artifactId = parts[count - 4]
        val version = parts[count - 3]
        val deps = mutableSetOf<PomDependency>()
        val iterator = lines.iterator()

        while (iterator.hasNext()) {
            val line = iterator.next().trim()

            if (line.startsWith("<dependency>")) {
                deps += parseDependencyBlock(path, iterator)
            }
        }
        return Pom(
            descriptor = PomDescriptor(
                groupId = groupId,
                artifactId = artifactId,
                version = version,
            ),
            dependencies = deps,
        )
    }

    private fun parseDependencyBlock(path: String, iterator: Iterator<String>): PomDependency {
        var groupId: String? = null
        var artifactId: String? = null
        var version = ""
        var scope = PomDependencyScope.COMPILE // Default scope
        var inExclusionBlock = false // TODO: consider POM <exclusions> block

        while (iterator.hasNext()) {
            val line = iterator.next().trim()

            if (line.startsWith("<exclusions>") && !inExclusionBlock) {
                inExclusionBlock = true
            } else if (line.startsWith("</exclusions>") && inExclusionBlock) {
                inExclusionBlock = false
            }
            if (!inExclusionBlock) {
                when {
                    line.startsWith("<groupId>") -> groupId = parseValue(line)
                    line.startsWith("<artifactId>") -> artifactId = parseValue(line)
                    line.startsWith("<version>") -> version = parseValue(line)
                    line.startsWith("<scope>") -> scope = parseScope(parseValue(line))
                    line.startsWith("</dependency>") -> break
                }
            }
        }
        return PomDependency(
            descriptor = PomDescriptor(
                groupId = checkNotNull(groupId) {"No value for groupId in $path"},
                artifactId = checkNotNull(artifactId) {"No value for artifactId in $path"},
                version = version,
            ),
            scope = scope,
        )
    }

    private fun parseScope(value: String) = PomDependencyScope.valueOf(value.uppercase())

    private fun parseValue(line: String): String {
        val start = line.indexOf(">")
        val end = line.indexOf("</")

        if (start == -1 || end == -1) {
            error("Failed to parse parameter: $line")
        }
        return line.substring(start + 1, end)
    }
}