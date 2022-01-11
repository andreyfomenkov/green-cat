package ru.fomenkov.plugin.task.config

import com.google.gson.Gson
import ru.fomenkov.plugin.task.Task
import ru.fomenkov.plugin.util.Telemetry
import java.io.File

class ReadPluginConfigTask(private val configFilePath: String) : Task<String, PluginConfiguration>(configFilePath) {

    override fun body(): PluginConfiguration {
        val file = File(configFilePath)

        if (!file.exists()) {
            error("No configuration file found: $configFilePath")
        }
        val text = file.readText()
        val configuration = Gson().fromJson(text, PluginConfiguration::class.java)

        Telemetry.verboseLog("Plugin configuration:")
        Telemetry.verboseLog(configuration.toString())
        return configuration
    }
}