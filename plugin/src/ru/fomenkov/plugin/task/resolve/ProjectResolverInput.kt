package ru.fomenkov.plugin.task.resolve

data class ProjectResolverInput(
    val propertiesFileName: String,  // Project's gradle.properties (version placeholders)
    val settingsFileName: String,    // Project's settings.gradle (all modules)
    val androidSdkPath: String,
    val greencatRoot: String,
    val mappedModules: Map<String, String>,
)