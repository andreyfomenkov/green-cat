package ru.fomenkov.runner.update

import ru.fomenkov.runner.KOTLINC_DIR
import ru.fomenkov.runner.KOTLINC_VERSION_FILE
import ru.fomenkov.runner.logger.Log
import ru.fomenkov.runner.params.RunnerParams
import ru.fomenkov.runner.ssh.ssh

class CompilerUpdater(
    updateTimestampFile: String,
    artifactVersionInfoUrl: String,
) : Updater(updateTimestampFile, artifactVersionInfoUrl) {

    override fun checkForUpdate(params: RunnerParams, forceCheck: Boolean) {
        val remoteCompilerDateFile = "${params.greencatRoot}/$KOTLINC_DIR/$KOTLINC_VERSION_FILE"
        val exists = ssh { cmd("ls $remoteCompilerDateFile && echo OK") }.find { line -> line.trim() == "OK" } != null
        val archiveFile = "kotlinc.zip"
        ssh { cmd("mkdir -p ${params.greencatRoot}") }

        fun downloadUpdate(version: String, artifactUrl: String) {
            Log.d("[Compiler] Downloading update...")
            ssh { cmd("rm -rf ${params.greencatRoot}/$KOTLINC_DIR") }
            ssh { cmd("cd ${params.greencatRoot} && curl -s $artifactUrl > $archiveFile && unzip $archiveFile && rm $archiveFile") }
            ssh { cmd("cd ${params.greencatRoot}/$KOTLINC_DIR && echo \"$version\" > $KOTLINC_VERSION_FILE") }

            val isUpdated = ssh { cmd("ls ${params.greencatRoot}/$KOTLINC_DIR/bin/kotlinc && echo OK") }.find { line -> line.trim() == "OK" } != null
            when {
                isUpdated -> Log.d("[Compiler] Done")
                else -> error("Error updating compiler on the remote host")
            }
        }
        if (exists) {
            if (forceCheck || isNeedToCheckVersion()) {
                Log.d("[Compiler] Checking for update...")

                val curVersion = ssh { cmd("head -1 $remoteCompilerDateFile") }.firstOrNull()?.trim() ?: ""
                val (updVersion, artifactUrl) = getVersionInfo()

                if (curVersion == updVersion) {
                    Log.d("[Compiler] Everything is up to date")
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