package ru.fomenkov.runner

import ru.fomenkov.plugin.util.*
import ru.fomenkov.runner.diff.GitDiffParser
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.ParamsReader
import ru.fomenkov.runner.params.RunnerMode
import ru.fomenkov.runner.params.RunnerParams
import ru.fomenkov.runner.ssh.setRemoteHost
import ru.fomenkov.runner.ssh.ssh
import java.io.File
import kotlin.random.Random

fun main(args: Array<String>) = try {
    val time = timeMillis { launch(args) }
    displayTotalTime(time)

} catch (error: Throwable) {
    when (error.message.isNullOrBlank()) {
        true -> Log.e("Process execution failed")
        else -> Log.e("Process execution failed. ${error.message}")
    }
}

private fun launch(args: Array<String>) {
    Log.d("Starting GreenCat Runner")
    Log.d("Project on GitHub: $PROJECT_GITHUB\n")
    validateShellCommands()

    val params = readParams(args) ?: return
    setRemoteHost(host = params.sshHost)

    val supported = checkGitDiff() ?: return
    syncWithMainframer(params, supported)
    checkForUpdate(params)
    startGreenCatPlugin(params)
    pushDexToAndroidDevice(params)
    restartApplication(params)
}

private fun restartApplication(params: RunnerParams) {
    when (params.mode) {
        is RunnerMode.Debug -> {
            Log.d("Restarting application...")

            val action = "android.intent.action.MAIN"
            val category = "android.intent.category.LAUNCHER"
            val componentName = params.mode.componentName
            val appPackage = componentName.split("/").first()
            exec("adb shell am force-stop $appPackage")
            exec("adb shell am start -n $componentName -a $action -c $category")
        }
        is RunnerMode.UiTest -> {
            Log.d("Starting UI test...")
            // TODO: implement
        }
    }
}

private fun pushDexToAndroidDevice(params: RunnerParams) {
    val tmpDir = exec("echo \$TMPDIR").firstOrNull() ?: ""
    check(tmpDir.isNotBlank()) { "Failed to get /tmp directory" }

    exec("scp ${params.sshHost}:${params.greencatRoot}/$DEX_FILES_DIR/$OUTPUT_DEX_FILE $tmpDir")
    val output = exec("adb push $tmpDir/$OUTPUT_DEX_FILE $DEVICE_DEX_DIR/$OUTPUT_DEX_FILE")

    if (output.find { line -> line.contains("error:") } != null) {
        output.forEach(Telemetry::err)
        error("Failed to push DEX file via adb")
    }
}

private fun displayTotalTime(time: Long) {
    val str = "│  Build & deploy complete in ${formatMillis(time)}  │"
    val border = "─".repeat(str.length - 2)
    val space = " ".repeat(str.length - 2)

    Log.d("\n")
    Log.d("╭$border╮")
    Log.d("│$space│")
    Log.d(str)
    Log.d("│$space│")
    Log.d("╰$border╯")
}

private fun startGreenCatPlugin(params: RunnerParams) {
    val greencatJar = "${params.greencatRoot}/$GREENCAT_JAR"
    val version = ssh { cmd("java -jar $greencatJar -v") }.firstOrNull() ?: "???"
    Log.d("Launching GreenCat v$version on the remote host")

    val mappedModulesParam = formatMappedModulesParameter(params.modulesMap)
    val output = ssh(print = true) {
        cmd("cd ${params.projectRoot}")
        cmd("java -jar $greencatJar -s ${params.androidSdkRoot} -g ${params.greencatRoot} $mappedModulesParam")
    }
    output.forEach(Log::d)
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

private fun formatMappedModulesParameter(mappedModules: Map<String, String>) =
    when (mappedModules.isEmpty()) {
        true -> ""
        else -> {
            "-a " + mappedModules.entries.joinToString(separator = ",") { (moduleFrom, moduleTo) ->
                "$moduleFrom:$moduleTo"
            }
        }
    }

private fun isNeedToCheckVersion(): Boolean {
    return Random.nextInt(from = 1, until = 5) == 3 // TODO: store timestamp
}

private fun checkForUpdate(params: RunnerParams) {
    val remoteJarPath = "${params.greencatRoot}/$GREENCAT_JAR"
    val exists = ssh { cmd("ls $remoteJarPath && echo OK") }.find { line -> line.trim() == "OK" } != null

    fun getVersionInfo(): Pair<String, String> {
        val lines = exec("curl -s $ARTIFACT_VERSION_INFO_URL")

        if (lines.size == 2) {
            val version = lines.first().trim()
            val artifactUrl = lines.last().trim()
            return version to artifactUrl
        } else {
            lines.forEach { line -> Log.e("[CURL] $line") }
            error("Failed to download version-info")
        }
    }
    fun downloadUpdate(version: String, artifactUrl: String) {
        Log.d("Downloading update...")
        val tmpDir = exec("echo \$TMPDIR").firstOrNull() ?: ""

        check(tmpDir.isNotBlank()) { "Unable to get /tmp directory" }
        exec("curl -s $artifactUrl > $tmpDir/$GREENCAT_JAR")

        val tmpJarPath = "$tmpDir/$GREENCAT_JAR"

        if (!File(tmpJarPath).exists()) {
            error("Error downloading $GREENCAT_JAR to /tmp directory")
        }
        ssh { cmd("rm $remoteJarPath") }
        val isDeleted = ssh { cmd("ls $remoteJarPath && echo OK") }.find { line -> line.trim() == "OK" } == null

        if (!isDeleted) {
            error("Failed to delete old JAR version on the remote host")
        }
        exec("scp $tmpJarPath ${params.sshHost}:$remoteJarPath")
        val isUpdated = ssh { cmd("ls $remoteJarPath && echo OK") }.find { line -> line.trim() == "OK" } != null

        when {
            isUpdated -> Log.d("Done. GreenCat updated to v$version")
            else -> error("Error uploading $GREENCAT_JAR to the remote host")
        }
    }
    if (exists) {
        if (isNeedToCheckVersion()) {
            val curVersion = ssh { cmd("java -jar $remoteJarPath -v") }.firstOrNull()?.trim() ?: ""
            val (updVersion, artifactUrl) = getVersionInfo()

            if (curVersion == updVersion) {
                Log.d("Everything is up to date")
            } else {
                downloadUpdate(updVersion, artifactUrl)
            }
        }
    } else {
        val (updVersion, artifactUrl) = getVersionInfo()
        downloadUpdate(updVersion, artifactUrl)
    }
}

private fun syncWithMainframer(
    params: RunnerParams,
    supported: List<String>,
) {
    Log.d("\nSync with the remote host...")

    ssh {
        cmd("mkdir -p ${params.greencatRoot}")
        cmd("cd ${params.greencatRoot}")
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
    listOf("git", "adb", "find", "rm", "ls", "ssh", "scp", "curl").forEach { cmd ->
        val exists = exec("command -v $cmd").isNotEmpty()

        if (!exists) {
            error("Command '$cmd' not found")
        }
    }
}

private const val PROJECT_GITHUB = "https://github.com/andreyfomenkov/green-cat"
private const val GREENCAT_JAR = "greencat.jar"
const val CLASSPATH_DIR = "cp"
const val SOURCE_FILES_DIR = "src"
const val CLASS_FILES_DIR = "class"
const val DEX_FILES_DIR = "dex"
const val OUTPUT_DEX_FILE = "delta.dex"
const val DEVICE_DEX_DIR = "/sdcard/greencat"
// TODO: change URL with the branch name (master-v2 -> master)
private const val ARTIFACT_VERSION_INFO_URL = "https://raw.githubusercontent.com/andreyfomenkov/green-cat/master-v2/artifacts/version-info"