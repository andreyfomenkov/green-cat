package ru.fomenkov.runner.diff

import ru.fomenkov.plugin.util.exec
import java.io.File

class GitDiffParser {

    fun parse(): GitDiffResult {
        val paths = mutableSetOf<String>()
        var branch = ""
        var untracked = false

        exec("git status")
            .filterNot(String::isBlank)
            .map(String::trim)
            .forEach { line ->
                if (branch.isBlank() && line.startsWith(ON_BRANCH)) {
                    branch = line.substring(ON_BRANCH.length, line.length).trim()
                }
                if (!untracked && line.lowercase().contains("untracked files")) {
                    untracked = true
                }
                if (untracked) {
                    if (!line.contains("(") && !line.contains("\"") && !line.contains(":")) {
                        when (line.trim().endsWith("/")) {
                            true -> {
                                paths += exec("find ${line.trim()}")
                                    .map { path -> path.trim() }
                                    .map { path -> path.replace("//", "/") }
                                    .filterNot { path -> path.endsWith("/") }
                            }
                            else -> {
                                paths += line.trim()
                            }
                        }
                    }
                } else {
                    when {
                        line.startsWith(GIT_NEW_FILE) -> {
                            paths += line.substring(GIT_NEW_FILE.length, line.length).trim()
                        }
                        line.startsWith(GIT_MODIFIED_FILE) -> {
                            paths += line.substring(GIT_MODIFIED_FILE.length, line.length).trim()
                        }
                    }
                }
            }
        check(branch.isNotBlank()) { "Failed to parse branch name" }
        validatePaths(paths)

        return GitDiffResult(
            branch = branch,
            paths = paths,
        )
    }

    private fun validatePaths(paths: Set<String>) {
        paths.forEach { path ->
            if (!File(path).exists()) {
                error("File doesn't exist: $path")
            }
        }
    }

    private companion object {
        const val ON_BRANCH = "On branch"
        const val GIT_NEW_FILE = "new file:"
        const val GIT_MODIFIED_FILE = "modified:"
    }
}