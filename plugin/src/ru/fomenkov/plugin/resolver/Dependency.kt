package ru.fomenkov.plugin.resolver

sealed class Dependency {

    data class Files(val modulePath: String, val filePath: String, val relation: Relation) : Dependency()

    data class Project(val moduleName: String, val relation: Relation) : Dependency()

    data class Library(val artifact: String, val version: String, val relation: Relation) : Dependency()
}

enum class Relation {
    IMPLEMENTATION,
    API,
}