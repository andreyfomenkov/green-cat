package ru.fomenkov.plugin.resolver

sealed class Dependency {

    data class Files(val modulePath: String, val filePath: String, val relation: Relation) : Dependency()

    data class Project(val moduleName: String, val relation: Relation) : Dependency()

    // Blank version means using the latest version for this artifact
    data class Library(val artifact: String, val version: String = "", val relation: Relation) : Dependency() {

        fun isUseLatestVersion() = version.isBlank()
    }

    // TODO: refactor
    fun isTransitive() = when (this) {
        is Files -> relation.isTransitive()
        is Project -> relation.isTransitive()
        is Library -> relation.isTransitive()
    }
}

enum class Relation {
    // Non-transitive
    IMPLEMENTATION,
    DEBUG_IMPLEMENTATION,
    // Transitive
    COMPILE_ONLY,
    API,
    // Test
    ANDROID_TEST_IMPLEMENTATION,
    TEST_IMPLEMENTATION;

    fun isTransitive() = this == API || this == COMPILE_ONLY
}