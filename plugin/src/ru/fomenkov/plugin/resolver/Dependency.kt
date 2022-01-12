package ru.fomenkov.plugin.resolver

sealed class Dependency {

    data class Files(val modulePath: String, val filePath: String, val relation: Relation) : Dependency()

    data class Project(val moduleName: String, val relation: Relation) : Dependency()

    // Blank version means using the latest version for this artifact
    data class Library(val artifact: String, val version: String = "", val relation: Relation) : Dependency() {

        fun isUseLatestVersion() = version.isBlank()
    }
}

enum class Relation {
    IMPLEMENTATION,
    API,
}