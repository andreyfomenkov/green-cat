package ru.fomenkov.plugin.repository.data

sealed class RepositoryResource {

    data class ClassResource(
        val packageName: String,
        val classFilePath: String,
    ) : RepositoryResource()

    data class JarResource(
        val packageName: String,
        val jarFilePath: String,
    ) : RepositoryResource()
}