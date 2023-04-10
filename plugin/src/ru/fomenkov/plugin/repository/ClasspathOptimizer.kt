package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.util.CURRENT_DIR
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.noTilda
import java.io.File

class ClasspathOptimizer {
    private val transformsDir = "~/.gradle/caches/transforms-3".noTilda()
    private val modulesDir = "~/.gradle/caches/modules-2/files-2.1".noTilda()

    fun optimize(classpath: Set<String>): Set<String> {
        createSymlinks()

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
                    path
                        .replace(transformsDir, transformsDir.dirName())
                        .replace(modulesDir, modulesDir.dirName())
                }
            }.toSet()
    }

    private fun createSymlinks() {
        exec("ln -s $transformsDir .")
        exec("ln -s $modulesDir .")
    }

    private fun String.dirName() = split("/").last()

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