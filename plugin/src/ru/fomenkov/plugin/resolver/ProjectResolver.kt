package ru.fomenkov.plugin.resolver

import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
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
                    var value = parts.last().trim()

                    if (value.endsWith("@aar")) {
                        value = value.substring(0, value.length - 4)
                    }
                    map += property to value
                } else {
//                    Telemetry.verboseErr("[$propertiesFileName] Skipping line: $line")
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

    fun parseModuleBuildGradleFile(moduleName: String): Set<Dependency> {
        Telemetry.verboseLog("Parsing $moduleName/build.gradle")
        val deps = mutableSetOf<Dependency>()
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
                        line.startsWith(FILES_IMPLEMENTATION_PREFIX) || line.startsWith(FILES_API_PREFIX) -> {
                            parseFilesDependency(moduleName, line)
                        }
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
//                        Telemetry.verboseErr("[$path] Skipping line: $line")
                    }
                }
            }
        return deps
    }

    fun findAllJarsInGradleCache(path: String): Set<String> {
        Telemetry.verboseLog("Get all JARs in Gradle cache")

        val jars = exec("find $path -name '*.jar'")
            .filter { line -> line.trim().endsWith(".jar") }
        if (jars.isEmpty()) {
            error("No .jar files found in directory: $path")
        }
        return jars.toSet()
    }

    fun findAllAarsInGradleCache(path: String): Set<String> {
        Telemetry.verboseLog("Get all AARs in Gradle cache")

        val aars = exec("find $path -name '*.aar'")
            .filter { line -> line.trim().endsWith(".aar") }
        if (aars.isEmpty()) {
            error("No .aar files found in directory: $path")
        }
        return aars.toSet()
    }

    private fun parseFilesDependency(moduleName: String, line: String): Dependency? {
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
            line.startsWith(FILES_IMPLEMENTATION_PREFIX) -> {
                Dependency.Files(moduleName = moduleName, filePath = filePath, relation = Relation.IMPLEMENTATION)
            }
            line.startsWith(FILES_API_PREFIX) -> {
                Dependency.Files(moduleName = moduleName, filePath = filePath, relation = Relation.API)
            }
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
            // implementation group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.11.2'
            if (line.contains("group:") && contains("name:") && contains("version:")) {
                val parts = line.split(",")

                if (parts.size != 3) {
                    error("[Library dependency] Failed to parse line: $line\"")
                }
                val group = parts[0].let {
                    val startIndex = it.indexOf("'")
                    val endIndex = it.lastIndexOf("'")
                    it.substring(startIndex + 1, endIndex)
                }
                val name = parts[1].let {
                    val startIndex = it.indexOf("'")
                    val endIndex = it.lastIndexOf("'")
                    it.substring(startIndex + 1, endIndex)
                }
                "$group:$name"
            } else {
                val startIndex = line.indexOf("'")
                val endIndex = line.lastIndexOf(":")

                if (startIndex == -1 || endIndex == -1) {
                    error("[Library dependency] Failed to parse line: $line")
                }
                substring(startIndex + 1, endIndex)
            }
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
        const val FILES_IMPLEMENTATION_PREFIX = "implementation files"
        const val FILES_API_PREFIX = "api files"
        const val PROJECT_IMPLEMENTATION_PREFIX = "implementation project"
        const val PROJECT_API_PREFIX = "api project"
        const val LIBRARY_IMPLEMENTATION_PREFIX = "implementation"
        const val LIBRARY_API_PREFIX = "api"
    }
}