package ru.fomenkov.plugin.task.config

class PluginConfiguration(
    val ignoredModules: Set<String>,
    val ignoredLibs: Set<String>,
) {

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("ignored modules: $ignoredModules\n")
        builder.append("ignored libs: $ignoredLibs")
        return builder.toString()
    }
}