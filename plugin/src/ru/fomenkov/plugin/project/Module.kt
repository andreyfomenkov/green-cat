package ru.fomenkov.plugin.project

data class Module(
    val name: String,
    val path: String,
    val children: Set<Module>,
    val parents: Set<Module>,
    val libraries: Set<Library>,
) {

    override fun toString() = StringBuilder("# Module: $name #\n").apply {
        append("Path: $path\n")
        append("${children.size} child module(s), ${parents.size} parent module(s), ${libraries.size} lib(s)\n")
        children.forEach { module -> append("[CHILD] ${module.name}\n") }
        parents.forEach { module -> append("[PARENT] ${module.name}\n") }
        libraries.forEach { library -> append("[LIB] ${library.name}:${library.version}\n") }
    }.toString()
}