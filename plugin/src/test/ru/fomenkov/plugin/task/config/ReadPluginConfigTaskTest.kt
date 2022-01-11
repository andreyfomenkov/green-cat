package ru.fomenkov.plugin.task.config

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.task.Result
import ru.fomenkov.plugin.util.fromResources
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadPluginConfigTaskTest {

    @Test
    fun `Test read from plugin configuration file`() {
        val result = ReadPluginConfigTask(fromResources("greencat.configuration")).run()

        assertTrue(result is Result.Complete, "Failed to parse plugin configuration")
        assertEquals(3, result.output.ignoredModules.size, "Expecting 3 ignored modules")
        assertEquals(2, result.output.ignoredLibs.size, "Expecting 2 ignored libs")
    }
}