package ru.fomenkov.runner.update

import ru.fomenkov.plugin.util.exec
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.RunnerParams
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

abstract class Updater(
    private val updateTimestampFile: String,
    private val artifactVersionInfoUrl: String,
) {

    protected fun isNeedToCheckVersion(): Boolean {
        val tmpDir = exec("echo \$TMPDIR").firstOrNull() ?: ""
        check(tmpDir.isNotBlank()) { "Failed to get /tmp directory" }
        val updateFile = File("$tmpDir/$updateTimestampFile")

        fun writeTimestamp() {
            val timestampNow = System.currentTimeMillis()
            updateFile.writeText(timestampNow.toString())
        }
        return if (updateFile.exists()) {
            val timestampLastUpdate = updateFile.readText().toLong()
            val timestampNow = System.currentTimeMillis()

            if (abs(timestampNow - timestampLastUpdate) > CHECK_UPDATE_INTERVAL) {
                writeTimestamp()
                true
            } else {
                false
            }
        } else {
            writeTimestamp()
            true
        }
    }

    protected fun getVersionInfo(): Pair<String, String> {
        val lines = exec("curl -s $artifactVersionInfoUrl")

        if (lines.size == 2) {
            val version = lines.first().trim()
            val artifactUrl = lines.last().trim()
            return version to artifactUrl
        } else {
            lines.forEach { line -> Log.e("[CURL] $line") }
            error("Failed to download version-info")
        }
    }

    abstract fun checkForUpdate(params: RunnerParams, forceCheck: Boolean)

    private companion object {
        val CHECK_UPDATE_INTERVAL = TimeUnit.HOURS.toMillis(1)
    }
}