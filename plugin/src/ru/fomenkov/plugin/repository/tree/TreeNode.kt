package ru.fomenkov.plugin.repository.tree

data class TreeNode<T>(
    var value: T? = null,
    val nodes: MutableMap<String, TreeNode<T>> = mutableMapOf(),
)