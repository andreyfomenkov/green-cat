package ru.fomenkov.plugin.repository.tree

import java.util.*

class Tree<T> {

    private val head = TreeNode<T>()

    fun setValue(route: List<String>, value: T) {
        var curr = head

        route.forEach { step ->
            var next = curr.nodes[step]

            if (next == null) {
                next = TreeNode(value = null, nodes = mutableMapOf())
                curr.nodes[step] = next
            }
            curr = next
        }
        curr.value = value
    }

    fun getValue(route: List<String>) = getNode(route)?.value

    fun getAllValues(route: List<String> = emptyList()): List<T> {
        val result = mutableListOf<T>()
        val node = when (route.isEmpty()) {
            true -> head
            else -> getNode(route)
        }
        if (node != null) {
            val queue = LinkedList<TreeNode<T>>()
            queue += node

            while (queue.isNotEmpty()) {
                val curr = queue.poll()
                val value = curr.value

                if (value != null) {
                    result += value
                }
                queue += curr.nodes.values
            }
        }
        return result
    }

    fun size() = getAllValues().size

    private fun getNode(route: List<String>): TreeNode<T>? {
        var curr = head

        route.forEach { step ->
            curr = curr.nodes[step] ?: return null
        }
        return curr
    }
}