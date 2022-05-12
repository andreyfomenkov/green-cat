package ru.fomenkov.plugin.repository.imports

data class Import(
    val packageName: String,
    val isStatic: Boolean,
    val hasTrailingWildcard: Boolean,
) {
    fun parts() = packageName.split(".")
}