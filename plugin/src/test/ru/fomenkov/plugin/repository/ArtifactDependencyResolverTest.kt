package ru.fomenkov.plugin.repository

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.repository.parser.JetifiedResourceParser
import ru.fomenkov.plugin.repository.parser.MetadataDescriptionParser
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec

// Playground
class ArtifactDependencyResolverTest {

    @Test
    fun test() {
        val jetifiedResourceParser = JetifiedResourceParser()
        val jetifiedJarRepository = JetifiedJarRepository(jetifiedResourceParser)
        val descriptionParser = MetadataDescriptionParser()
        val resolver = MetadataArtifactDependencyResolver(jetifiedJarRepository, descriptionParser)

        exec("find ~/.gradle/caches/modules-2/metadata-2.97 -name 'descriptor.bin'")
            .map { path ->
                val parts = path.split("/")
                val groupId = parts[parts.size - 5]
                val artifact = parts[parts.size - 4]
                val version = parts[parts.size - 3]
                MetadataDescriptionParser.Artifact(groupId, artifact, version)
            }
            .toSet()
            .forEach { artifact ->
                val paths = resolver.resolvePaths(artifact.groupId, artifact.artifactId, artifact.version)

                Telemetry.log("\n$artifact (${paths.size}) paths")
                paths.forEach { path -> Telemetry.log(" - $path") }
            }
    }
}