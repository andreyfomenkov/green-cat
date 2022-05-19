package ru.fomenkov.plugin.repository.data

data class PomDescriptor(
    val groupId: String,
    val artifactId: String,
    val version: String, // Can be empty for provided scope
)

data class PomDependency(
    val descriptor: PomDescriptor,
    val scope: PomDependencyScope,
)

data class Pom(
    val descriptor: PomDescriptor,
    val dependencies: Set<PomDependency>,
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