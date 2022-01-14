package ru.fomenkov.plugin.resolver

data class CacheResource(
    val pkg: String,
    val artifact: String,
    val version: String,
    val resource: String,
    val fullPath: String,
)