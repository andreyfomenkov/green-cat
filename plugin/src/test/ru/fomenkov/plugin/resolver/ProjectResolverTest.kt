package ru.fomenkov.plugin.resolver

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.util.fromResources
import kotlin.test.assertEquals

class ProjectResolverTest {

    private val resolver = ProjectResolver(
        propertiesFileName = fromResources("test-project/gradle.properties"),
        settingsFileName = fromResources("test-project/settings.gradle"),
    )

    @Test
    fun `Test read GRADLE properties file`() {
        val versions = resolver.parseGradleProperties()

        assertEquals(6, versions.size, "Versions map must contain 6 entries")
        assertEquals("123", versions["library_a.version"])
        assertEquals("2.1", versions["library_b.version"])
        assertEquals("3.2.1", versions["library_c.version"])
        assertEquals("3.2.1-alpha", versions["library_d.version"])
        assertEquals("1.2.3", versions["library_e.version"])
        assertEquals("44.55.66", versions["library_f.version"])
    }
}