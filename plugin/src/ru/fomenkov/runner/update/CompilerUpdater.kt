package ru.fomenkov.runner.update

import ru.fomenkov.plugin.util.exec
import ru.fomenkov.runner.GREENCAT_JAR
import ru.fomenkov.runner.KOTLINC_DIR
import ru.fomenkov.runner.KOTLINC_VERSION_FILE
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.RunnerParams
import ru.fomenkov.runner.ssh.ssh
import java.io.File

class CompilerUpdater(
    updateTimestampFile: String,
    artifactVersionInfoUrl: String,
) : Updater(updateTimestampFile, artifactVersionInfoUrl) {

    override fun checkForUpdate(params: RunnerParams, forceCheck: Boolean) {
        val archiveFile = "kotlinc.zip"
        val remoteCompilerDateFile = "${params.greencatRoot}/$KOTLINC_DIR/$KOTLINC_VERSION_FILE"
        val remoteZipPath = "${params.greencatRoot}/$archiveFile"
        val exists = ssh { cmd("ls $remoteCompilerDateFile && echo OK") }.find { line -> line.trim() == "OK" } != null
        ssh { cmd("mkdir -p ${params.greencatRoot}") }

        fun downloadUpdate(version: String, artifactUrl: String) {
            Log.d("Downloading compiler... ", newLine = false)
            val tmpDir = exec("echo \$TMPDIR").firstOrNull() ?: ""
            val tmpZipPath = "$tmpDir/$archiveFile"

            check(tmpDir.isNotBlank()) { "Unable to get /tmp directory" }
            exec("curl -s $artifactUrl > $tmpZipPath")

            if (!File(tmpZipPath).exists()) {
                error("Error downloading $archiveFile to /tmp directory")
            }
            exec("scp $tmpZipPath ${params.sshHost}:$remoteZipPath")
            File(tmpZipPath).delete()

            ssh { cmd("rm -rf ${params.greencatRoot}/$KOTLINC_DIR") }
            ssh { cmd("cd ${params.greencatRoot} && unzip $archiveFile && rm $archiveFile") }
            ssh { cmd("cd ${params.greencatRoot}/$KOTLINC_DIR && echo \"$version\" > $KOTLINC_VERSION_FILE") }

            val isUpdated = ssh { cmd("ls ${params.greencatRoot}/$KOTLINC_DIR/bin/kotlinc && echo OK") }.find { line -> line.trim() == "OK" } != null
            when {
                isUpdated -> Log.d("Done")
                else -> error("Error updating compiler on the remote host")
            }
        }
        if (exists) {
            if (forceCheck || isNeedToCheckVersion()) {
                Log.d("[Compiler] Checking for update... ", newLine = false)

                val curVersion = ssh { cmd("head -1 $remoteCompilerDateFile") }.firstOrNull()?.trim() ?: ""
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