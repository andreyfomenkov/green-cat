package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.data.RepositoryResource
import java.util.jar.JarEntry

abstract class JarRepository : ResourceRepository<RepositoryResource.JarResource>() {

    protected fun getPackageName(entry: JarEntry): String? {
        val str = entry.toString()

        return if (str.endsWith(".class")) {
            val endIndex = str.length - 6
            str.substring(0, endIndex).replace('/', '.')
        } else {
            null
        }
    }
}