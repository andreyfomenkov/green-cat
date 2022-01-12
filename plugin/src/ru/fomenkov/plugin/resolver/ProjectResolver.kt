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
                        line.hasFilesImplementationPrefix() || line.hasFilesApiPrefix() -> {
                            parseFilesDependency(modulePath, line)
                        }
                        line.hasProjectImplementationPrefix() || line.hasProjectApiPrefix() -> {
                            parseModuleDependency(line)
                        }
                        line.hasLibraryImplementationPrefix() || line.hasLibraryApiPrefix() -> {
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
            line.hasProjectImplementationPrefix() -> Dependency.Project(moduleName = moduleName, relation = Relation.IMPLEMENTATION)
            line.hasProjectApiPrefix() -> Dependency.Project(moduleName = moduleName, relation = Relation.API)
            else -> null
        }
    }

    private fun parseLibraryDependency(line: String): Dependency? {
        val relation = when {
            line.hasLibraryImplementationPrefix() -> Relation.IMPLEMENTATION
            line.hasLibraryApiPrefix() -> Relation.API
            else -> return null
        }
        val extractVersion = { line: String ->
            val lastColonIndex = line.lastIndexOf(":")
            line.substring(lastColonIndex + 1, line.length).run {
                split("'").find(::isVersionOrPlaceholder) ?: error("[Library dependency] Failed to extract version: $line")
            }
        }
        val artifact: String
        val version: String

        line.run {
            if (line.contains("group:") && contains("name:") && contains("version:")) {
                val parts = line.split(",")

                if (parts.size != 3) {
                    error("[Library dependency] Failed to parse line: $line")
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
                artifact = "$group:$name"
                version = extractVersion(line)

            } else if (line.contains("rootProject")) {
                val parts = line.split("rootProject")

                if (parts.size != 2) {
                    error("[Library dependency] Failed to parse line: $line")
                }
                val left = parts[0]
                val right = parts[1]
                // TODO: improve and check for errors
                artifact = left.substring(left.indexOf("'") + 1, left.lastIndexOf(":"))
                val start = right.indexOf("'") + 1
                version = right.substring(start, right.indexOf("'", start))

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
        return Dependency.Library(artifact = artifact, version = version, relation = relation)
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

    // TODO: need to optimize
    private fun String.collapse() = replace(" ", "").replace("(", "").trim()

    private fun String.hasFilesImplementationPrefix() = collapse().startsWith("implementationfiles")

    private fun String.hasFilesApiPrefix() = collapse().startsWith("apifiles")

    private fun String.hasProjectImplementationPrefix() = collapse().startsWith("implementationproject")

    private fun String.hasProjectApiPrefix() = collapse().startsWith("apiproject")

    private fun String.hasLibraryImplementationPrefix() = collapse().startsWith("implementation")

    private fun String.hasLibraryApiPrefix() = collapse().startsWith("api")

    private companion object {
        const val BUILD_GRADLE_FILE_NAME = "build.gradle"
        const val DEPENDENCIES_BLOCK_START = "dependencies"
        const val DEPENDENCIES_BLOCK_END = "}"
    }
}