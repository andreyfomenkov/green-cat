package ru.fomenkov.plugin.task.resolve

data class ProjectResolverOutput(
    val sourceFilesClasspath: Map<String, Set<String>>, // Classpath for each input source file
    val sourceFilesCompileOrder: Map<String, Int>, // Compilation order for each input source file
)