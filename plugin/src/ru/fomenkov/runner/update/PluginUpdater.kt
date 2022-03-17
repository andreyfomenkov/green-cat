package ru.fomenkov.runner.update

import ru.fomenkov.plugin.util.exec
import ru.fomenkov.runner.ARTIFACT_VERSION_INFO_URL
import ru.fomenkov.runner.CHECK_UPDATE_INTERVAL
import ru.fomenkov.runner.GREENCAT_JAR
import ru.fomenkov.runner.PLUGIN_UPDATE_TIMESTAMP_FILE
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.RunnerParams
import ru.fomenkov.runner.ssh.ssh
import java.io.File
import kotlin.math.abs

class PluginUpdater : Updater {

    override fun isNeedToCheckVersion(): Boolean {
        val tmpDir = exec("echo \$TMPDIR").firstOrNull() ?: ""
        check(tmpDir.isNotBlank()) { "Failed to get /tmp directory" }
        val updateFile = File("$tmpDir/$PLUGIN_UPDATE_TIMESTAMP_FILE")

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

    override fun checkForUpdate(params: RunnerParams, forceCheck: Boolean) {
        val remoteJarPath = "${params.greencatRoot}/$GREENCAT_JAR"
        val exists = ssh { cmd("ls $remoteJarPath && echo OK") }.find { line -> line.trim() == "OK" } != null
        ssh { cmd("mkdir -p ${params.greencatRoot}") }

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
            Log.d("[Plugin] Downloading update...")
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
                isUpdated -> Log.d("[Plugin] Done. GreenCat updated to v$version")
                else -> error("Error uploading $GREENCAT_JAR to the remote host")
            }
        }
        if (exists) {
            if (forceCheck || isNeedToCheckVersion()) {
                Log.d("[Plugin] Checking for update...")

                val curVersion = ssh { cmd("java -jar $remoteJarPath -v") }.firstOrNull()?.trim() ?: ""
                val (updVersion, artifactUrl) = getVersionInfo()

                if (curVersion == updVersion) {
                    Log.d("[Plugin] Everything is up to date")
                } else {
                    downloadUpdate(updVersion, artifactUrl)
                }
            }
        } else {
            val (updVersion, artifactUrl) = getVersionInfo()
            downloadUpdate(updVersion, artifactUrl)
        }
    }
}