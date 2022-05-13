package ru.fomenkov.plugin.repository.parser

import ru.fomenkov.plugin.repository.ResourceRepository
import ru.fomenkov.plugin.repository.data.Import
import ru.fomenkov.plugin.repository.data.RepositoryResource
import ru.fomenkov.plugin.util.Telemetry
import java.io.File
import java.lang.StringBuilder

class SourceFileReader(
    private val importParser: ImportParser,
    private vararg val repositories: ResourceRepository<*>,
) {

    fun parseImports(path: String, verbose: Boolean = false): List<Import> {
        val file = File(path)

        if (file.extension != "java" && file.extension != "kt") {
            throw IllegalArgumentException("Source file must be either Java class or Kotlin file")
        }
        val imports = file.readLines().mapNotNull(importParser::parse)

        if (verbose) {
            Telemetry.log("File: $path")
            Telemetry.log("Number of imports: ${imports.size}")

            imports.forEachIndexed { index, import ->
                val builder = StringBuilder("[$index] ${import.packageName()}")

                when {
                    import.isStatic -> builder.append(", static")
                    import.hasTrailingWildcard -> builder.append(", wildcard")
                }
                Telemetry.log(builder.toString())
            }
        }
        return imports
    }

    fun resolveImports(imports: List<Import>, verbose: Boolean = false): ResolverOutput {
        val resolved = mutableMapOf<Import, RepositoryResource>()
        val unresolved = mutableSetOf<Import>()

        imports.forEach { import ->
            val resource = findInRepositories(import)

            if (resource == null) {
                unresolved += import
            } else {
                resolved += import to resource
            }
        }
        if (verbose) {
            if (resolved.isNotEmpty()) {
                Telemetry.log("Resolved imports:")
                resolved.forEach { (import, resource) -> Telemetry.log(" + $import -> $resource") }
            }
            if (unresolved.isNotEmpty()) {
                Telemetry.log("Unresolved imports:")
                unresolved.forEach { import -> Telemetry.log(" - $import") }
            }
        }
        return ResolverOutput(resolved, unresolved)
    }

    private fun findInRepositories(import: Import): RepositoryResource? {
        repositories.forEach { repo ->
            val resource = repo.find(import.packageName())
            
            if (resource != null) {
                return resource
            }
        }
        return null
    }

    data class ResolverOutput(
        val resolvedImports: Map<Import, RepositoryResource>,
        val unresolvedImports: Set<Import>,
    )
}