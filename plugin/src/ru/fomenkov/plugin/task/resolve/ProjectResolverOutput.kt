package ru.fomenkov.plugin.task.resolve

data class ProjectResolverOutput(
    val sourceFilesMap: Map<String, Set<String>>, // Module name -> source files
    val moduleClasspathMap: Map<String, String>, // Module name -> module classpath
    val moduleCompilationOrderMap: Map<String, Int>, // Module name -> compilation order
)