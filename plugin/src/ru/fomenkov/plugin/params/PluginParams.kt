package ru.fomenkov.plugin.params

data class PluginParams(
    val androidSdkRoot: String,
    val greencatRoot: String,
    val mappedModules: Map<String, String>,
    val deviceApiLevel: String,
)

enum class Param(val key: String) {
    MAPPED_MODULES("-a"),
    ANDROID_SDK_ROOT("-s"),
    GREENCAT_ROOT("-g"),
    DEVICE_API_LEVEL("-l"),
    VERSION("-v"),
}