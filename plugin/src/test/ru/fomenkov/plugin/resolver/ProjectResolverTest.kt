package ru.fomenkov.plugin.resolver

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.task.resolve.GradleCacheItem
import ru.fomenkov.plugin.util.fromResources
import ru.fomenkov.plugin.util.isVersionGreaterOrEquals
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val properties = mutableMapOf<String, String>() // Not required for this test
        val deps = resolver.parseModuleBuildGradleFile(modulePath = fromResources("test-project/module-1"), properties)
            .toMutableSet()

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
        Dependency.Project(moduleName = "module-4", relation = Relation.DEBUG_IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Project(moduleName = "module-5", relation = Relation.COMPILE_ONLY)
            .assertContainsAndDelete(deps)
        Dependency.Project(moduleName = "module-2", relation = Relation.TEST_IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Project(moduleName = "module-3", relation = Relation.ANDROID_TEST_IMPLEMENTATION)
            .assertContainsAndDelete(deps)

        // Libs
        Dependency.Library(artifact = "com.google.guava:guava", version = "19.0", relation = Relation.API)
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "com.google.android.gms:play-services-cast",
            version = "16.0.3",
            relation = Relation.API
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(artifact = "org.jmdns:jmdns", version = "3.5.1", relation = Relation.IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "ru.ok.android-android2:duration-interval",
            version = "library_b.version",
            relation = Relation.IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "com.facebook.device.yearclass:yearclass",
            version = "library_a.version",
            relation = Relation.API
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "com.google.android:library",
            version = "library_c.version",
            relation = Relation.IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "me.leolin:ShortcutBadger",
            version = "1.1.22",
            relation = Relation.IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(artifact = "com.android:library-controls", version = "", relation = Relation.IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "com.google.guava:guava",
            version = "project.ext.guavaVersion",
            relation = Relation.TEST_IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(artifact = "junit:junit", version = "junit.version", relation = Relation.TEST_IMPLEMENTATION)
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "org.robolectric:robolectric",
            version = "robolectric.version",
            relation = Relation.ANDROID_TEST_IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "org.apache.commons:commons-lang3",
            version = "3.4",
            relation = Relation.ANDROID_TEST_IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "com.android:common-api",
            version = "2.1.456-SNAPSHOT",
            relation = Relation.ANDROID_TEST_IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "com.android:test-library",
            version = "1.6.65",
            relation = Relation.ANDROID_TEST_IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "com.google.firebase:firebase-messaging",
            version = "",
            relation = Relation.API
        )
            .assertContainsAndDelete(deps)
        Dependency.Library(
            artifact = "com.google.firebase:firebase-crashlytics",
            version = "",
            relation = Relation.IMPLEMENTATION
        )
            .assertContainsAndDelete(deps)

        assertTrue(
            deps.isEmpty(),
            "Collection is not empty. The following item(s) are left:\n${formatDepsListMessage(deps)}"
        )
    }

    @Test
    fun `Test resolve library versions from placeholders`() {
        val versions = resolver.validateAndResolveLibraryVersions(
            modulePath = fromResources("test-project/module-1"),
            deps = setOf(
                Dependency.Library(
                    artifact = "com.library_a",
                    version = "lib_a.version",
                    relation = Relation.DEBUG_IMPLEMENTATION
                ),
                Dependency.Project(moduleName = "module-2", relation = Relation.DEBUG_IMPLEMENTATION),
                Dependency.Library(artifact = "com.library_b", version = "0.1.2", relation = Relation.IMPLEMENTATION),
                Dependency.Project(moduleName = "module-3", relation = Relation.IMPLEMENTATION),
                Dependency.Library(artifact = "com.library_c", version = "", relation = Relation.COMPILE_ONLY),
                Dependency.Project(moduleName = "module-4", relation = Relation.COMPILE_ONLY),
                Dependency.Library(artifact = "com.library_d", version = "lib_d.version", relation = Relation.API),
                Dependency.Project(moduleName = "module-5", relation = Relation.API),
            ),
            properties = mapOf(
                "lib_a.version" to "1.2.3",
                "lib_d.version" to "4.5.6",
                "lib_e.version" to "10.20.30",
                "lib_f.version" to "40.50.60",
            ),
            moduleDeclarations = setOf(
                ModuleDeclaration(name = "module-2", path = fromResources("test-project/module-2")),
                ModuleDeclaration(name = "module-3", path = fromResources("test-project/module-3")),
                ModuleDeclaration(name = "module-4", path = fromResources("test-project/module-4")),
                ModuleDeclaration(name = "module-5", path = fromResources("test-project/module-5")),
            ),
        )
        assertEquals(4, versions.size, "Expecting 4 libraries with resolved versions")
        assertEquals("1.2.3", versions["com.library_a"])
        assertEquals("0.1.2", versions["com.library_b"])
        assertEquals("", versions["com.library_c"]) // Empty value for the artifact last version
        assertEquals("4.5.6", versions["com.library_d"])
    }

    @Test
    fun `Test compare versions`() {
        assertTrue("1".isVersionGreaterOrEquals("1"))
        assertTrue("1.0".isVersionGreaterOrEquals("1"))
        assertTrue("2.0".isVersionGreaterOrEquals("1.0"))
        assertTrue("2.1".isVersionGreaterOrEquals("2.0"))
        assertTrue("8.12".isVersionGreaterOrEquals("8.11"))
        assertTrue("8.12.0".isVersionGreaterOrEquals("8.11.0"))
        assertTrue("8.12.0".isVersionGreaterOrEquals("8.11.9"))
        assertTrue("8.12.0".isVersionGreaterOrEquals("8.11.9999999999"))
        assertFalse("3".isVersionGreaterOrEquals("4"))
        assertFalse("3.4".isVersionGreaterOrEquals("3.4.1"))
        assertFalse("3.4.2".isVersionGreaterOrEquals("3.4.3"))
    }

    @Test
    fun `Test get single artifact archive paths from Gradle cache`() {
        // All JARs for artifact "com.google.dagger:dagger" in preloaded Gradle cache:
        //
        // .../com.google.dagger/dagger/2.40.1/45790480cd353dffe5ce508dd4158cd46b066612/dagger-2.40.1.jar
        // .../com.google.dagger/dagger/2.40.1/bfd11bec52269c47134ac54d03eec187ac1cdb2/dagger-2.40.1-javadoc.jar
        // .../com.google.dagger/dagger/2.40.1/bc9d7272bcf2ad118de657d1b2013470b5328e60/dagger-2.40.1-sources.jar
        // .../com.google.dagger/dagger/2.28.3/10d83810ef9e19714116ed518896c90c6606d633/dagger-2.28.3.jar
        // .../com.google.dagger/dagger/2.28.3/ab17752a49d8c92a0d5132538e7861d7b6c47443/dagger-2.28.3-sources.jar

        // Preloaded JAR / AAR paths from Gradle cache
        var startIndex = 0
        val dirFrom = "files-2.1"
        val allPaths = File(fromResources("gradle_cache_paths")).readLines()
            .map { line ->
                if (startIndex == 0) {
                    startIndex = line.indexOf(dirFrom) + dirFrom.length + 1
                }
                val parts = line.substring(startIndex, line.length).split("/")

                GradleCacheItem.Archive(
                    pkg = parts[0],
                    artifact = parts[1],
                    version = parts[2],
                    resource = parts[4],
                    fullPath = line,
                )
            }
            .toSet()

        // 2.40.1 found in Gradle cache -> choose 2.40.1
        assertArtifactArchivePaths(artifact = "com.google.dagger:dagger", version = "2.40.1", allPaths) {
            setOf(
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/45790480cd353dffe5ce508dd4158cd46b066612/dagger-2.40.1.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/bfd11bec52269c47134ac54d03eec187ac1cdb2/dagger-2.40.1-javadoc.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/bc9d7272bcf2ad118de657d1b2013470b5328e60/dagger-2.40.1-sources.jar",
            )
        }
        // 2.28.3 found in Gradle cache, but have newer -> choose 2.28.3
        assertArtifactArchivePaths(artifact = "com.google.dagger:dagger", version = "2.28.3", allPaths) {
            setOf(
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.28.3/10d83810ef9e19714116ed518896c90c6606d633/dagger-2.28.3.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.28.3/ab17752a49d8c92a0d5132538e7861d7b6c47443/dagger-2.28.3-sources.jar",
            )
        }
        // 1.10.5 not found in Gradle cache -> choose the closest one: 2.28.3
        assertArtifactArchivePaths(artifact = "com.google.dagger:dagger", version = "1.10.5", allPaths) {
            setOf(
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.28.3/10d83810ef9e19714116ed518896c90c6606d633/dagger-2.28.3.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.28.3/ab17752a49d8c92a0d5132538e7861d7b6c47443/dagger-2.28.3-sources.jar",
            )
        }
        // 2.34.4 not found in Gradle cache -> choose the closest one: 2.40.1
        assertArtifactArchivePaths(artifact = "com.google.dagger:dagger", version = "2.34.4", allPaths) {
            setOf(
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/45790480cd353dffe5ce508dd4158cd46b066612/dagger-2.40.1.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/bfd11bec52269c47134ac54d03eec187ac1cdb2/dagger-2.40.1-javadoc.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/bc9d7272bcf2ad118de657d1b2013470b5328e60/dagger-2.40.1-sources.jar",
            )
        }
        // Empty version means any version from Gradle cache -> choose the latest one: 2.40.1
        assertArtifactArchivePaths(artifact = "com.google.dagger:dagger", version = "", allPaths) {
            setOf(
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/45790480cd353dffe5ce508dd4158cd46b066612/dagger-2.40.1.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/bfd11bec52269c47134ac54d03eec187ac1cdb2/dagger-2.40.1-javadoc.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/bc9d7272bcf2ad118de657d1b2013470b5328e60/dagger-2.40.1-sources.jar",
            )
        }
        // 4.0.0 not found in Gradle cache -> choose the closest one: 2.40.1
        assertArtifactArchivePaths(artifact = "com.google.dagger:dagger", version = "4.0.0", allPaths) {
            setOf(
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/45790480cd353dffe5ce508dd4158cd46b066612/dagger-2.40.1.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/bfd11bec52269c47134ac54d03eec187ac1cdb2/dagger-2.40.1-javadoc.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.40.1/bc9d7272bcf2ad118de657d1b2013470b5328e60/dagger-2.40.1-sources.jar",
            )
        }
        // 8.9.4 not found in Gradle cache -> choose the closest one: 8.12.30
        assertArtifactArchivePaths(
            artifact = "com.googlecode.libphonenumber:libphonenumber",
            version = "8.9.4",
            allPaths
        ) {
            setOf(
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.googlecode.libphonenumber/libphonenumber/8.12.30/54fa9f1ab2e8c8ce1417424fc4ed95ff7c9ebfb/libphonenumber-8.12.30.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.googlecode.libphonenumber/libphonenumber/8.12.30/58c7b42d217f06712837c34972a8a78e6968ce6b/libphonenumber-8.12.30-sources.jar",
                "/Users/andrey/.gradle/caches/modules-2/files-2.1/com.googlecode.libphonenumber/libphonenumber/8.12.30/877a19cebba1bcc0f015acd5ad4e0bbb05b9059e/libphonenumber-8.12.30-javadoc.jar",
            )
        }
    }

    @Test
    fun `Test build module dependencies case 1`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            ),
            "m1" to setOf(
                Dependency.Project(moduleName = "m2", relation = Relation.API),
            ),
            "m2" to setOf(
                Dependency.Project(moduleName = "m3", relation = Relation.IMPLEMENTATION),
                Dependency.Project(moduleName = "m4", relation = Relation.IMPLEMENTATION),
                Dependency.Project(moduleName = "m5", relation = Relation.API),
            ),
            "m3" to emptySet(),
            "m4" to emptySet(),
            "m5" to emptySet(),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m2", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m5", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build module dependencies case 2`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            ),
            "m1" to setOf(
                Dependency.Project(moduleName = "m2", relation = Relation.IMPLEMENTATION),
            ),
            "m2" to setOf(
                Dependency.Project(moduleName = "m3", relation = Relation.IMPLEMENTATION),
            ),
            "m3" to emptySet(),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build module dependencies case 3`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            ),
            "m1" to setOf(
                Dependency.Project(moduleName = "m2", relation = Relation.API),
            ),
            "m2" to setOf(
                Dependency.Project(moduleName = "m3", relation = Relation.IMPLEMENTATION),
            ),
            "m3" to emptySet(),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m2", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build module dependencies case 4`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            ),
            "m1" to setOf(
                Dependency.Project(moduleName = "m2", relation = Relation.API),
            ),
            "m2" to setOf(
                Dependency.Project(moduleName = "m3", relation = Relation.API),
            ),
            "m3" to emptySet(),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m2", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m3", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build module dependencies case 5`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            ),
            "m1" to setOf(
                Dependency.Project(moduleName = "m2", relation = Relation.IMPLEMENTATION),
                Dependency.Project(moduleName = "m3", relation = Relation.API),
            ),
            "m2" to setOf(
                Dependency.Project(moduleName = "m3", relation = Relation.API),
            ),
            "m3" to setOf(
                Dependency.Project(moduleName = "m4", relation = Relation.API),
            ),
            "m4" to emptySet(),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m3", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m4", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build all module dependencies case 1`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
                Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.IMPLEMENTATION),
                Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.IMPLEMENTATION),
            ),
            "m1" to setOf(
                Dependency.Library(artifact = "com.android:libB", version = "2.0", relation = Relation.IMPLEMENTATION),
                Dependency.Files(modulePath = "m1", filePath = "libB.jar", relation = Relation.IMPLEMENTATION),
            ),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.IMPLEMENTATION),
            Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build all module dependencies case 2`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.DEBUG_IMPLEMENTATION),
                Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.API),
                Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.COMPILE_ONLY),
            ),
            "m1" to setOf(
                Dependency.Library(artifact = "com.android:libB", version = "2.0", relation = Relation.IMPLEMENTATION),
                Dependency.Files(modulePath = "m1", filePath = "libB.jar", relation = Relation.IMPLEMENTATION),
            ),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.IMPLEMENTATION),
            Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build all module dependencies case 3`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.DEBUG_IMPLEMENTATION),
                Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.API),
                Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.COMPILE_ONLY),
            ),
            "m1" to setOf(
                Dependency.Library(artifact = "com.android:libB", version = "2.0", relation = Relation.API),
                Dependency.Files(modulePath = "m1", filePath = "libB.jar", relation = Relation.COMPILE_ONLY),
            ),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.IMPLEMENTATION),
            Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.IMPLEMENTATION),
            Dependency.Library(artifact = "com.android:libB", version = "2.0", relation = Relation.IMPLEMENTATION),
            Dependency.Files(modulePath = "m1", filePath = "libB.jar", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build all module dependencies case 4`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.DEBUG_IMPLEMENTATION),
                Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.API),
                Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.COMPILE_ONLY),
            ),
            "m1" to setOf(
                Dependency.Project(moduleName = "m2", relation = Relation.API),
            ),
            "m2" to setOf(
                Dependency.Project(moduleName = "m3", relation = Relation.API),
            ),
            "m3" to setOf(
                Dependency.Library(artifact = "com.android:libB", version = "2.0", relation = Relation.COMPILE_ONLY),
                Dependency.Files(modulePath = "m1", filePath = "libB.jar", relation = Relation.API),
            ),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m2", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m3", relation = Relation.IMPLEMENTATION),
            Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.IMPLEMENTATION),
            Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.IMPLEMENTATION),
            Dependency.Library(artifact = "com.android:libB", version = "2.0", relation = Relation.IMPLEMENTATION),
            Dependency.Files(modulePath = "m1", filePath = "libB.jar", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build all module dependencies case 5`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.DEBUG_IMPLEMENTATION),
                Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.API),
                Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.COMPILE_ONLY),
            ),
            "m1" to setOf(
                Dependency.Project(moduleName = "m2", relation = Relation.API),
            ),
            "m2" to setOf(
                Dependency.Project(moduleName = "m3", relation = Relation.IMPLEMENTATION),
            ),
            "m3" to setOf(
                Dependency.Library(artifact = "com.android:libB", version = "2.0", relation = Relation.COMPILE_ONLY),
                Dependency.Files(modulePath = "m1", filePath = "libB.jar", relation = Relation.API),
            ),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m2", relation = Relation.IMPLEMENTATION),
            Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.IMPLEMENTATION),
            Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test build all module dependencies case 6`() {
        mapOf(
            "app" to setOf(
                Dependency.Project(moduleName = "m1", relation = Relation.DEBUG_IMPLEMENTATION),
                Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.API),
                Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.COMPILE_ONLY),
            ),
            "m1" to setOf(
                Dependency.Project(moduleName = "m2", relation = Relation.API),
            ),
            "m2" to setOf(
                Dependency.Project(moduleName = "m3", relation = Relation.API),
            ),
            "m3" to setOf(
                Dependency.Library(
                    artifact = "com.android:libB",
                    version = "2.0",
                    relation = Relation.DEBUG_IMPLEMENTATION
                ),
                Dependency.Files(modulePath = "m1", filePath = "libB.jar", relation = Relation.IMPLEMENTATION),
            ),
        ).assertAppModuleChildProjects(
            Dependency.Project(moduleName = "m1", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m2", relation = Relation.IMPLEMENTATION),
            Dependency.Project(moduleName = "m3", relation = Relation.IMPLEMENTATION),
            Dependency.Library(artifact = "com.android:libA", version = "1.0", relation = Relation.IMPLEMENTATION),
            Dependency.Files(modulePath = "app", filePath = "libA.jar", relation = Relation.IMPLEMENTATION),
        )
    }

    @Test
    fun `Test modules compilation order case 1`() {
        val deps = mapOf(
            "m1" to setOf("m2"),
            "m2" to setOf("m3"),
            "m3" to emptySet(),
        )
        assertEquals(
            expected = mapOf(
                0 to setOf("m3"),
                1 to setOf("m2"),
                2 to setOf("m1"),
            ),
            actual = resolver.getModuleCompilationOrder(deps),
            message = "Unexpected compilation order",
        )
    }

    @Test
    fun `Test modules compilation order case 2`() {
        val deps = mapOf<String, Set<String>>(
            "m1" to emptySet(),
            "m2" to emptySet(),
            "m3" to emptySet(),
        )
        assertEquals(
            expected = mapOf(
                0 to setOf("m1", "m2", "m3"),
            ),
            actual = resolver.getModuleCompilationOrder(deps),
            message = "Unexpected compilation order",
        )
    }

    @Test
    fun `Test modules compilation order case 3`() {
        val deps = mapOf(
            "m1" to setOf("m2", "m3", "m4"),
            "m2" to emptySet(),
            "m3" to emptySet(),
            "m4" to setOf("m5", "m6", "m7"),
            "m5" to setOf("m8"),
            "m6" to setOf("m8"),
            "m7" to emptySet(),
            "m8" to emptySet(),
            "m9" to emptySet(),
        )
        assertEquals(
            expected = mapOf(
                0 to setOf("m2", "m3", "m7", "m8", "m9"),
                1 to setOf("m5", "m6"),
                2 to setOf("m4"),
                3 to setOf("m1"),
            ),
            actual = resolver.getModuleCompilationOrder(deps),
            message = "Unexpected compilation order",
        )
    }

    private fun Map<String, Set<Dependency>>.assertAppModuleChildProjects(vararg deps: Dependency) {
        assertEquals(
            expected = deps.toSet(),
            actual = resolver.getAllModuleDependencies(
                modulePath = "app", // Always check for 'app' module
                modules = this,
                moduleNameToPath = this.keys.associateBy { it }, // Map module name to path for testing
            ),
            message = "Module dependencies don's match",
        )
    }

    private fun assertArtifactArchivePaths(
        artifact: String,
        version: String,
        allPaths: Set<GradleCacheItem>,
        expectedPaths: () -> Set<String>
    ) {
        val cachePaths = mutableMapOf<String, Set<String>>()
        resolver.getArtifactArchivePaths(
            resolvedLibs = mapOf(artifact to version),
            cacheItems = allPaths,
            cachePaths = cachePaths,
        )
        assertEquals(expectedPaths(), cachePaths[artifact])
    }

    private fun formatDepsListMessage(deps: Set<Dependency>) = StringBuilder().apply {
        when (deps.isEmpty()) {
            true -> append(" - [COLLECTION IS EMPTY]")
            else -> deps.forEach { dependency -> append(" - $dependency\n") }
        }
    }.toString()

    private fun Dependency.assertContainsAndDelete(deps: MutableSet<Dependency>) {
        assertContains(
            deps,
            this,
            "Dependency not found:\n - $this\nDependencies left:\n${formatDepsListMessage(deps)}\n"
        )
        deps -= this
    }
}