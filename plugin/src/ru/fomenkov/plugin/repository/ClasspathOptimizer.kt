package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.util.CURRENT_DIR
import ru.fomenkov.plugin.util.HOME_DIR
import java.io.File

class ClasspathOptimizer {

    fun optimize(classpath: Set<String>): Set<String> {
        return classpath
            .asSequence()
            .map(::Entry)
            .toSet()
            .asSequence()
            .map { entry -> entry.path }
            .filterNot { path -> path.endsWith("/lint.jar") }
            .filterNot { path -> path.endsWith("-api.jar") }
            .filter { path ->
                val file = File(path)
                file.isFile || file.isDirectory && (file.list() ?: emptyArray()).isNotEmpty()
            }
            .map { path ->
                if (path.startsWith(CURRENT_DIR)) {
                    path.substring(CURRENT_DIR.length + 1, path.length)
                } else {
                    toRelativePath(path)
                }
            }.toSet()
    }

    private fun toRelativePath(path: String): String {
        val partsCount = CURRENT_DIR.subSequence(HOME_DIR.length + 1, CURRENT_DIR.length).split("/").size
        val parts = path.split("/").filter { part -> part.isNotBlank() }.toMutableList()

        for (i in 0 until partsCount) {
            parts[i] = ".."
        }
        return parts.joinToString(separator = "/")
    }

    private data class Entry(val path: String) {

        private val id: String = if (path.contains("/transformed/")) {
            val parts = path.split("/")
            val index = parts.indexOfLast { part -> part == "transformed" }
            check(index != -1) { "Failed to get directory index" }
            parts.subList(index + 1, parts.size).joinToString(separator = "/")
        } else {
            path
        }

        override fun hashCode() = id.hashCode()

        override fun equals(other: Any?) = other == id
    }
}