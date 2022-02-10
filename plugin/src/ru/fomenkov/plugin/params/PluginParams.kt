package ru.fomenkov.plugin.params

data class PluginParams(
    val androidSdkRoot: String,
    val greencatRoot: String,
    val mappedModules: Map<String, String>,
)

enum class Param(val key: String) {
    MAPPED_MODULES("-a"),
    ANDROID_SDK_ROOT("-s"),
    GREENCAT_ROOT("-g"),
    VERSION("-v")
}