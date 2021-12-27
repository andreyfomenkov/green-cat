package ru.fomenkov.plugin.task.resolve

data class GradleProjectInput(
    val propertiesFileName: String,       // gradle.properties (all versions)
    val settingsFileName: String, // settings.gradle (all modules)
)