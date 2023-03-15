package ru.fomenkov.runner.update

import ru.fomenkov.plugin.util.exec
import ru.fomenkov.runner.GREENCAT_JAR
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.RunnerParams
import ru.fomenkov.runner.ssh.ssh
import java.io.File

class PluginUpdater(
    updateTimestampFile: String,
    artifactVersionInfoUrl: String,
) : Updater(updateTimestampFile, artifactVersionInfoUrl) {

    override fun checkForUpdate(params: RunnerParams, forceCheck: Boolean) {
        val remoteJarPath = "${params.greencatRoot}/$GREENCAT_JAR"
        val exists = ssh { cmd("ls $remoteJarPath && echo OK") }.find { line -> line.trim() == "OK" } != null
        ssh { cmd("mkdir -p ${params.greencatRoot}") }

        fun downloadUpdate(version: String, artifactUrl: String) {
            Log.d("Downloading plugin... ", newLine = false)
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
            exec("rsync $tmpJarPath ${params.sshHost}:$remoteJarPath")
            val isUpdated = ssh { cmd("ls $remoteJarPath && echo OK") }.find { line -> line.trim() == "OK" } != null

            when {
                isUpdated -> Log.d("Done. GreenCat updated to v$version")
                else -> error("Error uploading $GREENCAT_JAR to the remote host")
            }
        }
        if (exists) {
            if (forceCheck || isNeedToCheckVersion()) {
                Log.d("[Plugin] Checking for update... ", newLine = false)

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
}