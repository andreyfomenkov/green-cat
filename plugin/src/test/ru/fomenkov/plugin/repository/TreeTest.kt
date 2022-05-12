package ru.fomenkov.plugin.repository

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.repository.tree.Tree
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TreeTest {

    @Test
    fun test() {
        val tree = Tree<Int>()

        tree.setValue(route = listOf("a"), 1)
        tree.setValue(route = listOf("a", "b"), 2)
        tree.setValue(route = listOf("a", "b", "c"), 3)
        tree.setValue(route = listOf("x", "y", "z"), 4)
        tree.setValue(route = listOf("x"), 5)

        assertEquals(1, tree.getValue(route = listOf("a")))
        assertEquals(2, tree.getValue(route = listOf("a", "b")))
        assertEquals(3, tree.getValue(route = listOf("a", "b", "c")))
        assertEquals(4, tree.getValue(route = listOf("x", "y", "z")))
        assertEquals(5, tree.getValue(route = listOf("x")))
        assertEquals(5, tree.size())

        assertNull(tree.getValue(route = listOf("x", "y")))
        assertNull(tree.getValue(route = listOf("a", "b", "c", "d")))
        assertNull(tree.getValue(route = listOf("e")))

        assertEquals(
            listOf(1, 2, 3, 4, 5),
            tree.getAllValues(route = emptyList()).sorted(),
        )
        assertEquals(
            listOf(1, 2, 3),
            tree.getAllValues(route = listOf("a")).sorted(),
        )
        assertEquals(
            listOf(2, 3),
            tree.getAllValues(route = listOf("a", "b")).sorted(),
        )
        assertEquals(
            listOf(3),
            tree.getAllValues(route = listOf("a", "b", "c")).sorted(),
        )
        assertEquals(
            listOf(4),
            tree.getAllValues(route = listOf("x", "y", "z")).sorted(),
        )
        assertEquals(
            listOf(4),
            tree.getAllValues(route = listOf("x", "y")).sorted(),
        )
        assertEquals(
            listOf(4, 5),
            tree.getAllValues(route = listOf("x")).sorted(),
        )
    }
}