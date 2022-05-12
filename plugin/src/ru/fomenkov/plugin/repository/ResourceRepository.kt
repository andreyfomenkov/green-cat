package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.tree.Tree
import ru.fomenkov.plugin.util.PackageNameUtil

abstract class ResourceRepository<T> {

    private val tree = Tree<T>()

    abstract fun scan()

    fun find(packageName: String): T? {
        val route = PackageNameUtil.split(packageName)
        return tree.getValue(route)
    }

    fun subtree(packageName: String): List<T> {
        val route = PackageNameUtil.split(packageName)
        return tree.getAllValues(route)
    }

    fun size() = tree.size()

    protected fun add(packageName: String, value: T) {
        val route = PackageNameUtil.split(packageName)
        tree.setValue(route, value)
    }
}