package ru.fomenkov.plugin.util

private const val SHELL = "/bin/zsh"

fun exec(cmd: String): List<String> {
    try {
        val output = mutableListOf<String>()
        Runtime.getRuntime().exec(arrayOf(SHELL, "-c", cmd)).apply {
            output += inputStream.bufferedReader().readLines()
            output += errorStream.bufferedReader().readLines()
        }
        return output
    } catch (error: Throwable) {
        throw RuntimeException("Failed to execute command: $cmd", error)
    }
}

fun <T> List<T>.print() = forEach(::println)