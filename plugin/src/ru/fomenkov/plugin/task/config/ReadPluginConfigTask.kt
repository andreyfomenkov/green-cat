package ru.fomenkov.plugin.task.config

import com.beust.klaxon.Klaxon
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.Telemetry
import java.io.File

class ReadPluginConfigTask(private val configFilePath: String) : Task<String, PluginConfiguration>(configFilePath) {

    override fun body(): PluginConfiguration {
        val file = File(configFilePath)

        if (!file.exists()) {
            error("No configuration file found: $configFilePath")
        }
        val configuration = checkNotNull(Klaxon().parse<PluginConfiguration>(file)) { "Unable to parse configuration" }

        Telemetry.verboseLog("Plugin configuration:")
        Telemetry.verboseLog(configuration.toString())
        return configuration
    }
}