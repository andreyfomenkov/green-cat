package ru.fomenkov.plugin.resolver

sealed class GradleCacheItem {

    data class Archive(
        val pkg: String,
        val artifact: String,
        val version: String,
        val resource: String,
        val fullPath: String,
    ) : GradleCacheItem()

    data class Pom(
        // Parent artifact
        val pkg: String,
        val artifact: String,
        val version: String,
        // And it's dependencies specified in .pom file
        val dependencies: Set<PomDependency>,
    ) : GradleCacheItem()
}

data class PomDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val scope: PomDependencyScope,
)

enum class PomDependencyScope {
    COMPILE,
    PROVIDED,
    RUNTIME,
    TEST,
    SYSTEM,
    IMPORT;

    fun isTransitive() = this == COMPILE // TODO: need to add more?
}