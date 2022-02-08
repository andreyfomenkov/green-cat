package ru.fomenkov.runner

import ru.fomenkov.plugin.util.isFileSupported
import ru.fomenkov.runner.diff.GitDiffParser
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.ParamsReader

fun main(args: Array<String>) {
    Log.d("Starting GreenCat Runner\n")
    checkCommandsExist()

    val params = when (args.isEmpty()) {
        true -> {
            ParamsReader.displayHelp()
            return
        }
        else -> ParamsReader(args).read()
    }
    Log.d("Checking diff...")
    val diff = GitDiffParser().parse()

    Log.d("On branch: ${diff.branch}")
    val (supported, ignored) = diff.paths.partition { path -> isFileSupported(path) }

    if (supported.isEmpty() && ignored.isEmpty()) {
        Log.d("Nothing to build")
        return
    }
    if (supported.isNotEmpty()) {
        Log.d("\nSource files to be compiled:\n")
        supported.sorted().forEach { path -> Log.d(" [*] $path") }
    }
    if (ignored.isNotEmpty()) {
        Log.d("\nNot supported:\n")
        ignored.sorted().forEach { path -> Log.d(" [-] $path") }
    }
}

private fun checkCommandsExist() {
    // TODO: git, adb, find, rm, ls, etc.
}