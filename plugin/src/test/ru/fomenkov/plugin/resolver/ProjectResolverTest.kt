package ru.fomenkov.plugin.resolver

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.util.fromResources
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

        assertEquals(7, declarations.size, "Declarations set must contain 7 items")
        assertContains(declarations, ModuleDeclaration(name = "module-1", path = "module-1"))
        assertContains(declarations, ModuleDeclaration(name = "module-2", path = "module-2"))
        assertContains(declarations, ModuleDeclaration(name = "module-3", path = "module-3"))
        assertContains(declarations, ModuleDeclaration(name = "module-4", path = "module-4"))
        assertContains(declarations, ModuleDeclaration(name = "module-5", path = "module-5"))
        assertContains(declarations, ModuleDeclaration(name = "module-6", path = "common/module-6"))
        assertContains(declarations, ModuleDeclaration(name = "module-7", path = "common/debug/module-7"))
    }

    @Test
    fun `Test parse build Gradle file for dependencies`() {
        // See ./plugin/src/res/test-project/module-1/build.gradle
        val deps = resolver.parseModuleBuildGradleFile(modulePath = fromResources("test-project/module-1")).toMutableSet()

        // Files
        Dependency.Files(
            modulePath = fromResources("test-project/module-1"),
            filePath = "module-1/some-library-2.1.0.jar",
            relation = Relation.IMPLEMENTATION,
        ).assertContainsAndDelete(deps)

        Dependency.Files(
            modulePath = fromResources("test-project/module-1"),
            filePath = "module-1/some-library-2.1.0.jar",
            relation = Relation.API,
        ).assertContainsAndDelete(deps)

        // Projects
        Dependency.Project(moduleName = "module-2", relation = Relation.IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Project(moduleName = "module-6", relation = Relation.IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Project(moduleName = "module-7", relation = Relation.API)
            .assertContainsAndDelete(deps)
        Dependency.Project(moduleName = "module-3", relation = Relation.API)
            .assertContainsAndDelete(deps)

        // Libs
        Dependency.Library(artifact = "com.google.guava:guava", version = "19.0", relation = Relation.API)
            .assertContainsAndDelete(deps)
        Dependency.Library(artifact = "com.google.android.gms:play-services-cast", version = "16.0.3", relation = Relation.API)
            .assertContainsAndDelete(deps)
        Dependency.Library(artifact = "org.jmdns:jmdns", version = "3.5.1", relation = Relation.IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Library(artifact = "ru.ok.android-android2:duration-interval", version = "library_b.version", relation = Relation.IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Library(artifact = "com.facebook.device.yearclass:yearclass", version = "library_a.version", relation = Relation.API)
            .assertContainsAndDelete(deps)
        Dependency.Library(artifact = "com.google.android:library", version = "android.library.version", relation = Relation.IMPLEMENTATION)
            .assertContainsAndDelete(deps)

        assertTrue(deps.isEmpty(), "Some dependencies are not resolved:\n${formatDepsListMessage(deps)}")
    }

    private fun formatDepsListMessage(deps: Set<Dependency>) = StringBuilder().apply {
        when (deps.isEmpty()) {
            true -> append(" - [COLLECTION IS EMPTY]")
            else -> deps.forEach { dependency -> append(" - $dependency\n") }
        }
    }.toString()

    private fun Dependency.assertContainsAndDelete(deps: MutableSet<Dependency>) {
        assertContains(deps, this, "Dependency not found:\n - $this\nDependencies left:\n${formatDepsListMessage(deps)}\n")
        deps -= this
    }
}