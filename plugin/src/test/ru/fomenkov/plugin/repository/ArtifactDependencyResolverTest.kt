package ru.fomenkov.plugin.repository

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.repository.parser.JetifiedResourceParser
import ru.fomenkov.plugin.repository.parser.PomFileParser

// Playground. Remove then
class ArtifactDependencyResolverTest {

    @Test
    fun test() {
        val jetifiedResourceParser = JetifiedResourceParser()
        val repository = JetifiedJarRepository(jetifiedResourceParser)
        val pomFileParser = PomFileParser()
        val resolver = ArtifactDependencyResolver(repository, pomFileParser)

        // androidx.core:core-ktx:1.1.0 -> 1.6.0
        // org.jetbrains.kotlin/kotlin-stdlib/1.6.10
        // org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.31 -> 1.6.10
        resolver.resolvePaths("org.jetbrains.kotlin", "kotlin-stdlib-jdk8", "1.6.10")
    }
}