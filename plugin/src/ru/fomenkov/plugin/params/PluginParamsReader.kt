package ru.fomenkov.plugin.params

class PluginParamsReader(
    private val args: Array<String>,
) {

    fun read(): PluginParams {
        val keyMap = mutableMapOf<String, String>()
        fun value(param: Param) = checkNotNull(keyMap[param.key]) { "No parameter: ${param.key}" }

        for (i in args.indices step 2) {
            val key = args[i]

            if (i == args.size - 1) {
                error("No value for parameter: $key")
            }
            val value = args[i + 1]
            keyMap += key to value
        }
        return PluginParams(
            androidSdkRoot = value(Param.ANDROID_SDK_ROOT),
            greencatRoot = value(Param.GREENCAT_ROOT),
            mappedModules = splitToMappedModules(keyMap[Param.MAPPED_MODULES.key]),
        )
    }

    private fun splitToMappedModules(value: String?) = when (value.isNullOrBlank()) {
        true -> emptyMap()
        else -> {
            val map = mutableMapOf<String, String>()
            value.split(",")
                .forEach { entry ->
                    val parts = entry.split(":")
                    check(parts.size == 2) { "Failed to get mapped modules from value: $value" }
                    val moduleFrom = parts.first()
                    val moduleTo = parts.last()
                    map += moduleFrom to moduleTo
                }
            map
        }
    }
}