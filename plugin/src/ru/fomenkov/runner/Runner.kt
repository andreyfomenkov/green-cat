package ru.fomenkov.runner

import ru.fomenkov.plugin.util.*
import ru.fomenkov.runner.diff.GitDiffParser
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.ParamsReader
import ru.fomenkov.runner.params.RunnerMode
import ru.fomenkov.runner.params.RunnerParams
import ru.fomenkov.runner.ssh.setRemoteHost
import ru.fomenkov.runner.ssh.ssh
import ru.fomenkov.runner.update.CompilerUpdater
import ru.fomenkov.runner.update.PluginUpdater
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

private val uiTestTaskExecutor = Executors.newSingleThreadExecutor()

private var displayTotalTime = false
private var uiTestTask: Future<List<String>>? = null

fun main(args: Array<String>) {
    try {
        Mixpanel.launch()
        var params: RunnerParams? = null
        val time = timeMillis { params = launch(args) }

        if (displayTotalTime) {
            displayTotalTime(time)
        }
        params?.apply(::restartApplication)
        Mixpanel.complete(duration = time)

        uiTestTask?.run {
            val uiTestOutput = try {
                get()
            } catch (e: Throwable) {
                error("Failed to get UI test output (message = ${e.localizedMessage})")
            }
            uiTestOutput.forEach(Telemetry::log)
        }
    } catch (error: Throwable) {
        // Wait and show error message at the end, because System.out and System.err
        // streams are not mutually synchronized
        Thread.sleep(FINAL_ERROR_MESSAGE_DELAY)
        Mixpanel.failed(message = error.message ?: "")

        when (error.message.isNullOrBlank()) {
            true -> Log.e("\n# Process execution failed #")
            else -> Log.e("\n# Process execution failed: ${error.message} #")
        }
    } finally {
        uiTestTaskExecutor.shutdown()
    }
}

private fun launch(args: Array<String>): RunnerParams? {
    Log.d("Starting GreenCat Runner")
    Log.d("Project on GitHub: $PROJECT_GITHUB\n")
    validateShellCommands()

    val pluginUpdater = PluginUpdater(PLUGIN_UPDATE_TIMESTAMP_FILE, PLUGIN_ARTIFACT_VERSION_INFO_URL)
    val compilerUpdated = CompilerUpdater(COMPILER_UPDATE_TIMESTAMP_FILE, COMPILER_ARTIFACT_VERSION_INFO_URL)
    val params = readParams(args) ?: return null
    setRemoteHost(host = params.sshHost)

    if (params.mode is RunnerMode.UiTest) {
        val testClass = params.mode.testClass
        val testRunner = params.mode.testRunner
        val callable = Callable { exec("adb shell am instrument -w -m -e waitPatch $WAIT_PATCH_DELAY -e debug false -e class '$testClass' $testRunner") }

        Log.d("Prelaunching UI test $testClass...")
        Log.d("Patch waiting timeout: ${WAIT_PATCH_DELAY / 1000L} sec\n")
        uiTestTask = uiTestTaskExecutor.submit(callable)
    }
    if (params.mode == RunnerMode.Update) {
        pluginUpdater.checkForUpdate(params, forceCheck = true)
        compilerUpdated.checkForUpdate(params, forceCheck = true)
        return null

    } else {
        checkSingleAndroidDeviceConnected()
        checkApplicationStoragePermissions(params)
        val supported = checkGitDiff() ?: return null
        syncWithMainframer(params, supported)
        pluginUpdater.checkForUpdate(params, forceCheck = false)
        compilerUpdated.checkForUpdate(params, forceCheck = false)
        startGreenCatPlugin(params)
        pushDexToAndroidDevice(params)
        displayTotalTime = true
        return params
    }
}

private fun checkSingleAndroidDeviceConnected() {
    val devices = exec("adb devices")
        .map { line -> line.trim() }
        .filter { line -> line.endsWith("device") }
        .map { line -> line.substring(0, line.length - 6).trim() }

    when (devices.size) {
        0 -> error("No Android devices connected")
        1 -> Telemetry.log("Device '${devices.first()}' connected (API ${getApiLevel()})")
        else -> error("Multiple devices connected")
    }
}

private fun checkApplicationStoragePermissions(params: RunnerParams) {
    val packageName = when (val mode = params.mode) {
        is RunnerMode.UiTest -> {
            mode.appPackage
        }
        is RunnerMode.Debug -> {
            mode.componentName.split("/").first()
        }
        else -> error("Unexpected runner mode: ${params.mode}")
    }
    val output = exec("adb shell dumpsys package $packageName | grep -i $READ_EXTERNAL_STORAGE_PERMISSION")
        .map { line -> line.lowercase().replace(" ", "") }

    if (output.isEmpty()) {
        error("No package '$packageName' installed")
    }
    output.forEach { line ->
        if (line.contains("granted=true")) {
            return
        } else if (line.contains("granted=false")) {
            error("Package '$packageName' has no external storage permission")
        }
    }
    error("Unable to check storage permissions for package '$packageName'")
}

private fun restartApplication(params: RunnerParams) {
    when (params.mode) {
        is RunnerMode.Debug -> {
            Log.d("\nRestarting application on the Android device...")

            val action = "android.intent.action.MAIN"
            val category = "android.intent.category.LAUNCHER"
            val componentName = params.mode.componentName
            val appPackage = componentName.split("/").first()
            exec("adb shell am force-stop $appPackage")
            exec("adb shell am start -n $componentName -a $action -c $category")
        }
        is RunnerMode.UiTest -> {
            // NOP, prelaunching on start
        }
        else -> {
            // NOP
        }
    }
}

private fun pushDexToAndroidDevice(params: RunnerParams) {
    val tmpDir = exec("echo \$TMPDIR").firstOrNull() ?: ""
    check(tmpDir.isNotBlank()) { "Failed to get /tmp directory" }

    exec("scp ${params.sshHost}:${params.greencatRoot}/$DEX_FILES_DIR/$OUTPUT_DEX_FILE $tmpDir")
    val output = exec("adb push $tmpDir/$OUTPUT_DEX_FILE $ANDROID_DEVICE_DEX_DIR/$OUTPUT_DEX_FILE")

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
    val apiLevel = getApiLevel() ?: 0
    Log.d("Launching GreenCat v$version on the remote host. It may take a while...")

    val mappedModulesParam = formatMappedModulesParameter(params.modulesMap)
    val lines = ssh(print = true) {
        cmd("cd ${params.projectRoot}")
        cmd("java -jar $greencatJar -s ${params.androidSdkRoot} -g ${params.greencatRoot} $mappedModulesParam -l $apiLevel")
    }
    lines.forEach { line ->
        if (line.trim().startsWith("Build failed:")) {
            error("error running plugin")
        }
    }
}

private fun checkGitDiff(): List<String>? {
    Log.d("Checking diff...")
    val gitDiffParser = GitDiffParser()
    val diff = gitDiffParser.parse()

    Log.d("On branch: ${diff.branch}")
    val (supported, ignored) = diff.paths.partition { path -> isFileSupported(path) }

    if (supported.isEmpty() && ignored.isEmpty()) {
        Log.d("Nothing to compile")
        return null
    }
    if (supported.isNotEmpty()) {
        Log.d("\nSource file(s) to be compiled:\n")
        supported.sorted().forEach { path -> Log.d(" [+] $path") }
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

private fun syncWithMainframer(
    params: RunnerParams,
    supported: List<String>,
) {
    Log.d("\nSync with the remote host...")

    ssh {
        cmd("mkdir -p ${params.greencatRoot}")
        cmd("cd ${params.greencatRoot}")
        cmd("mkdir $CLASSPATH_DIR")
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

const val PROJECT_GITHUB = "https://github.com/andreyfomenkov/green-cat"
const val PLUGIN_ARTIFACT_VERSION_INFO_URL = "https://raw.githubusercontent.com/andreyfomenkov/green-cat/master/artifacts/version-info"
const val COMPILER_ARTIFACT_VERSION_INFO_URL = "https://raw.githubusercontent.com/andreyfomenkov/kotlin-relaxed/relaxed-restrictions/artifact/date"
const val GREENCAT_JAR = "greencat.jar"
const val CLASSPATH_DIR = "cp"
const val SOURCE_FILES_DIR = "src"
const val CLASS_FILES_DIR = "class"
const val DEX_FILES_DIR = "dex"
const val KOTLINC_DIR = "kotlinc"
const val KOTLINC_VERSION_FILE = "date"
const val ANDROID_DEVICE_DEX_DIR = "/data/local/tmp"
const val OUTPUT_DEX_FILE = "patch.dex"
const val PLUGIN_UPDATE_TIMESTAMP_FILE = "greencat_update"
const val COMPILER_UPDATE_TIMESTAMP_FILE = "compiler_update"
const val READ_EXTERNAL_STORAGE_PERMISSION = "android.permission.READ_EXTERNAL_STORAGE"
const val FINAL_ERROR_MESSAGE_DELAY = 100L
const val WAIT_PATCH_DELAY = 60000L