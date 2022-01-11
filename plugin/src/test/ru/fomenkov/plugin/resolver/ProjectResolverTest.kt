package ru.fomenkov.plugin.resolver

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.util.fromResources
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ProjectResolverTest {

    private val resolver = ProjectResolver(
        propertiesFileName = fromResources("test-project/gradle.properties"),
        settingsFileName = fromResources("test-project/settings.gradle"),
    )

    @Test
    fun `Test parse Gradle properties file`() {
        val versions = resolver.parseGradleProperties()

        assertEquals(6, versions.size, "Versions map must contain 6 entries")
        assertEquals("123", versions["library_a.version"])
        assertEquals("2.1", versions["library_b.version"])
        assertEquals("3.2.1", versions["library_c.version"])
        assertEquals("3.2.1-alpha", versions["library_d.version"])
        assertEquals("1.2.3", versions["library_e.version"])
        assertEquals("44.55.66", versions["library_f.version"])
    }

    @Test
    fun `Test parse Gradle module declarations`() {
        val declarations = resolver.parseModuleDeclarations()

        assertEquals(7, declarations.size, "Declarations map must contain 7 entries")
        assertContains(declarations, ModuleDeclaration(name = "module-1", path = "module-1"))
        assertContains(declarations, ModuleDeclaration(name = "module-2", path = "module-2"))
        assertContains(declarations, ModuleDeclaration(name = "module-3", path = "module-3"))
        assertContains(declarations, ModuleDeclaration(name = "module-4", path = "module-4"))
        assertContains(declarations, ModuleDeclaration(name = "module-5", path = "module-5"))
        assertContains(declarations, ModuleDeclaration(name = "module-6", path = "common/module-6"))
        assertContains(declarations, ModuleDeclaration(name = "module-7", path = "common/debug/module-7"))
    }
}