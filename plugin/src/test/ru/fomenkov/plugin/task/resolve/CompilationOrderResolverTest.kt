package ru.fomenkov.plugin.task.resolve

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CompilationOrderResolverTest {

    private val resolver = CompilationOrderResolver()

    @Test
    fun `Test modules compilation order case 1`() {
        assertEquals(
            expected = mapOf(
                "A" to 0,
            ),
            actual = resolver.getModulesCompilationOrder(
                children = mapOf(
                    "A" to emptySet(),
                ),
            )
        )
    }

    @Test
    fun `Test modules compilation order case 2`() {
        assertEquals(
            expected = mapOf(
                "A" to 0,
                "B" to 0,
                "C" to 0,
            ),
            actual = resolver.getModulesCompilationOrder(
                children = mapOf(
                    "A" to emptySet(),
                    "B" to emptySet(),
                    "C" to emptySet(),
                ),
            )
        )
    }

    @Test
    fun `Test modules compilation order case 3`() {
        assertEquals(
            expected = mapOf(
                "A" to 2,
                "B" to 1,
                "C" to 0,
            ),
            actual = resolver.getModulesCompilationOrder(
                children = mapOf(
                    "A" to setOf("B"),
                    "B" to setOf("C"),
                    "C" to emptySet(),
                ),
            )
        )
    }

    @Test
    fun `Test modules compilation order case 4`() {
        assertEquals(
            expected = mapOf(
                "A" to 2,
                "B" to 1,
                "C" to 0,
            ),
            actual = resolver.getModulesCompilationOrder(
                children = mapOf(
                    "A" to setOf("B", "C"),
                    "B" to setOf("C"),
                    "C" to emptySet(),
                ),
            )
        )
    }

    @Test
    fun `Test modules compilation order case 5`() {
        assertEquals(
            expected = mapOf(
                "A" to 1,
                "B" to 0,
                "C" to 0,
            ),
            actual = resolver.getModulesCompilationOrder(
                children = mapOf(
                    "A" to setOf("B", "C"),
                    "B" to emptySet(),
                    "C" to emptySet(),
                ),
            )
        )
    }
}