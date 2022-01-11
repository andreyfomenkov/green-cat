package ru.fomenkov.plugin.task.resolve

data class GradleProjectInput(
    val propertiesFileName: String,   // gradle.properties (version placeholders)
    val settingsFileName: String,     // settings.gradle (all modules)
    val ignoredModules: Set<String>,
    val ignoredLibs: Set<String>,
)