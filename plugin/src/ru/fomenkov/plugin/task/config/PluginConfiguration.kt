package ru.fomenkov.plugin.task.config

class PluginConfiguration(
    val ignoredModules: Set<String>,
    val ignoredLibs: Set<String>,
    val androidSdkPath: String,
    val src: String, // TODO: temporary read from config file. Remove then
) {

    override fun toString() = StringBuilder().apply {
        append("Ignored modules: $ignoredModules\n")
        append("Ignored libs: $ignoredLibs")
        append("Android SDK path: $androidSdkPath\n")
        append("Source file: $src\n")
    }.toString()
}