package ru.fomenkov.plugin.project

data class Library(
    val name: String,
    val version: String,
    val cachePaths: Set<String>,
)