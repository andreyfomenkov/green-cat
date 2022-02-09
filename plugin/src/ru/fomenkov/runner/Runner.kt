package ru.fomenkov.runner

import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.isFileSupported
import ru.fomenkov.runner.diff.GitDiffParser
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.ParamsReader
import ru.fomenkov.runner.params.RunnerParams
import ru.fomenkov.runner.ssh.setRemoteHost
import ru.fomenkov.runner.ssh.ssh
import java.io.File

fun main(args: Array<String>) = try {
    launch(args)
} catch (error: Throwable) {
    when (error.message.isNullOrBlank()) {
        true -> Log.e("Process execution failed")
        else -> Log.e("Process execution failed. ${error.message}")
    }
}

private fun launch(args: Array<String>) {
    Log.d("Starting GreenCat Runner\n")
    validateShellCommands()

    val params = readParams(args) ?: return
    setRemoteHost(host = params.sshHost)

    val supported = checkGitDiff() ?: return
    syncWithMainframer(params, supported)

    if (isNeedUpdate(params)) {
        downloadUpdate()
    }
}

private fun checkGitDiff(): List<String>? {
    Log.d("Checking diff...")
    val diff = GitDiffParser().parse()

    Log.d("On branch: ${diff.branch}")
    val (supported, ignored) = diff.paths.partition { path -> isFileSupported(path) }

    if (supported.isEmpty() && ignored.isEmpty()) {
        Log.d("Nothing to compile")
        return null
    }
    if (supported.isNotEmpty()) {
        Log.d("\nSource file(s) to be compiled:\n")
        supported.sorted().forEach { path -> Log.d(" [*] $path") }
    }
    if (ignored.isNotEmpty()) {
        Log.d("\nIgnored (not supported):\n")
        ignored.sorted().forEach { path -> Log.d(" [-] $path") }
    }
    if (supported.isEmpty()) {
        Log.d("\nNo supported changes to compile")
        return null
    }
    return supported
}

private fun isNeedUpdate(params: RunnerParams): Boolean {
    val jarPath = "${params.greencatRoot}/$GREENCAT_JAR"
    val exists = ssh { cmd("ls $jarPath && echo OK") }.firstOrNull() == "OK"

    return if (exists) {
        Log.d("Checking for updates...")
        val version = ssh { cmd("java -jar $jarPath -v") }.firstOrNull()?.trim() ?: ""
        // TODO: periodic check with versions comparison
        false
    } else {
        true
    }
}

private fun downloadUpdate() {
    Log.d("Downloading update...")
}

private fun syncWithMainframer(
    params: RunnerParams,
    supported: List<String>,
) {
    Log.d("\nSync with the remote host...")

    ssh {
        cmd("mkdir -p ${params.greencatRoot}")
        cmd("cd ${params.greencatRoot}")
        cmd("rm -rf $CLASSPATH_DIR; mkdir $CLASSPATH_DIR")
        cmd("rm -rf $SOURCE_FILES_DIR; mkdir $SOURCE_FILES_DIR")
        cmd("rm -rf $CLASS_FILES_DIR; mkdir $CLASS_FILES_DIR")
        cmd("rm -rf $DEX_FILES_DIR; mkdir $DEX_FILES_DIR")

        supported
            .map { path -> File(path).parent }
            .forEach { dir -> cmd("mkdir -p $SOURCE_FILES_DIR/$dir") }
    }
    supported.forEach { path ->
        val dstPath = "${params.sshHost}:${params.greencatRoot}/$SOURCE_FILES_DIR/$path"
        exec("scp $path $dstPath").forEach { Log.d("[SCP] $it") }
    }
    val copiedSources = ssh { cmd("find ${params.greencatRoot}/$SOURCE_FILES_DIR") }
        .map(String::trim)
        .filter { path -> isFileSupported(path) }

    if (copiedSources.size < supported.size) {
        error("Not all files copied to the remote host")
    } else {
        Log.d("Copying ${copiedSources.size} source file(s) complete\n")
    }
}

private fun readParams(args: Array<String>) = when (args.isEmpty()) {
    true -> {
        ParamsReader.displayHelp()
        null
    }
    else -> ParamsReader(args).read()
}

private fun validateShellCommands() {
    listOf("git", "adb", "find", "rm", "ls", "ssh", "scp").forEach { cmd ->
        val exists = exec("command -v $cmd").isNotEmpty()

        if (!exists) {
            error("Command '$cmd' not found")
        }
    }
}

private const val CLASSPATH_DIR = "cp"
private const val SOURCE_FILES_DIR = "src"
private const val CLASS_FILES_DIR = "class"
private const val DEX_FILES_DIR = "dex"
private const val GREENCAT_JAR = "greencat.jar"