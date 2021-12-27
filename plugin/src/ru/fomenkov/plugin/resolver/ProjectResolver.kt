package ru.fomenkov.plugin.resolver

import ru.fomenkov.plugin.util.Telemetry
import java.io.File

class ProjectResolver(
    private val propertiesFileName: String,
    private val settingsFileName: String,
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
                    val value = parts.last().trim()
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
                            error("Failed to parse line: $line")
                        } else {
                            val name = line.substring(startIndex + 1, endIndex).replace(":", "/")
                            set += ModuleDeclaration(name = name, path = name,)
                        }
                    }
                    line.startsWith("'[") -> {
                        val startIndex = line.indexOf("[")
                        val endIndex = line.indexOf("'", 1)

                        if (startIndex == -1 || endIndex == -1) {
                            error("Failed to parse line: $line")
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

    fun parseModuleBuildGradleFile(moduleName: String): List<Dependency> {
        Telemetry.verboseLog("Parsing $moduleName/build.gradle")
        val deps = mutableListOf<Dependency>()
        val path = "$moduleName/$BUILD_GRADLE_FILE_NAME"
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
                        line.startsWith(PROJECT_IMPLEMENTATION_PREFIX) || line.startsWith(PROJECT_API_PREFIX) -> {
                            parseModuleDependency(line)
                        }
                        line.startsWith(LIBRARY_IMPLEMENTATION_PREFIX) || line.startsWith(LIBRARY_API_PREFIX) -> {
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

    private fun parseModuleDependency(line: String): Dependency? {
        val startIndex = line.lastIndexOf(":")
        val endIndex = line.lastIndexOf("'")

        if (startIndex == -1 || endIndex == -1) {
            error("Failed to parse line: $line")
        }
        val moduleName = line.substring(startIndex + 1, endIndex)
        return when {
            line.startsWith(PROJECT_IMPLEMENTATION_PREFIX) -> {
                Dependency.Project(moduleName = moduleName, relation = Relation.IMPLEMENTATION)
            }
            line.startsWith(PROJECT_API_PREFIX) -> {
                Dependency.Project(moduleName = moduleName, relation = Relation.API)
            }
            else -> null
        }
    }

    private fun parseLibraryDependency(line: String): Dependency? {
        val relation = when {
            line.startsWith(LIBRARY_IMPLEMENTATION_PREFIX) -> {
                Relation.IMPLEMENTATION
            }
            line.startsWith(LIBRARY_API_PREFIX) -> {
                Relation.API
            }
            else -> return null
        }
        val artifact = line.run {
            val startIndex = line.indexOf("'")
            val endIndex = line.lastIndexOf(":")

            if (startIndex == -1 || endIndex == -1) {
                error("Failed to parse line: $line")
            }
            substring(startIndex + 1, endIndex)
        }
        val lastColonIndex = line.lastIndexOf(":")
        val version = line.substring(lastColonIndex + 1, line.length).run {
            val part = split("'").find(::isVersionOrPlaceholder) ?: return null
            part
        }
        return Dependency.Library(artifact = artifact, version = version, relation = relation)
    }

    private fun isVersionOrPlaceholder(part: String): Boolean {
        if (part.isBlank()) {
            return false
        }
        part.forEach { c ->
            if (c != '.' && c != '-' && !c.isLetterOrDigit()) {
                return false
            }
        }
        return true
    }

    private companion object {
        const val BUILD_GRADLE_FILE_NAME = "build.gradle"
        const val DEPENDENCIES_BLOCK_START = "dependencies"
        const val DEPENDENCIES_BLOCK_END = "}"
        const val PROJECT_IMPLEMENTATION_PREFIX = "implementation project"
        const val PROJECT_API_PREFIX = "api project"
        const val LIBRARY_IMPLEMENTATION_PREFIX = "implementation"
        const val LIBRARY_API_PREFIX = "api"
    }
}