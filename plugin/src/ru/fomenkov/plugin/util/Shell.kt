package ru.fomenkov.plugin.util

private const val SHELL = "/bin/sh" // Keep sh for mainframer

fun exec(cmd: String, print: Boolean = false): List<String> {
    try {
        val output = mutableListOf<String>()
        Runtime.getRuntime().exec(arrayOf(SHELL, "-c", cmd)).apply {
            val inputReader = inputStream.bufferedReader()
            val errorReader = errorStream.bufferedReader()

            when (print) {
                true -> {
                    var line: String?
                    do {
                        line = errorReader.readLine() ?: break
                        output += line
                        Telemetry.err(line)
                    } while (line != null)
                    do {
                        line = inputReader.readLine() ?: break
                        output += line
                        Telemetry.log(line)
                    } while (line != null)
                }
                else -> {
                    output += inputReader.readLines() + errorReader.readLines()
                }
            }
        }
        return output

    } catch (error: Throwable) {
        Telemetry.err("Failed to execute shell command: $cmd\nError: ${error.message}")
        return emptyList()
    }
}