package ru.fomenkov.plugin.project

data class Module(
    val name: String,
    val path: String,
    val children: MutableSet<Module> = mutableSetOf(),
) {

    override fun toString() = StringBuilder("# Module: $name #\n").apply {
        append("Path: $path\n")
        children.forEach { module -> append("[CHILD] ${module.name}\n") }
    }.toString()
}