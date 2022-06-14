package ru.fomenkov.plugin.repository.parser

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.repository.JetifiedJarRepository
import kotlin.test.assertEquals

class JetifiedResourceParserTest {

    private val parser = JetifiedResourceParser()

    @Test
    fun `Test JAR resources inside transformed directory`() {
        // jetified, api
        assertEquals(
            JetifiedJarRepository.Entry("play-services-ads-identifier", "18.0.1"),
            parser.parse("~/.gradle/caches/transforms-3/ee61fea61f93c586b4a5c9757532036a/transformed/jetified-play-services-ads-identifier-18.0.1-api.jar"),
        )
        // jetified
        assertEquals(
            JetifiedJarRepository.Entry("kotlin-stdlib", "1.5.31"),
            parser.parse("~/.gradle/caches/transforms-3/624ba37502a20fb5cf6bcbefb0eca46d/transformed/jetified-kotlin-stdlib-1.5.31.jar"),
        )
        // api
        assertEquals(
            JetifiedJarRepository.Entry("ads-identifier", "1.0.0-alpha03"),
            parser.parse("~/.gradle/caches/transforms-3/1665e7f6142dc066b4a4db113e2e8cdc/transformed/ads-identifier-1.0.0-alpha03-api.jar"),
        )
        // plain
        assertEquals(
            JetifiedJarRepository.Entry("media", "1.4.1"),
            parser.parse("~/.gradle/caches/transforms-3/674bb7a92157d9ee02bd120f5791f911/transformed/media-1.4.1.jar"),
        )
    }

    @Test
    fun `Test AAR resources inside transformed directory`() {
        // jetified, api
        assertEquals(
            JetifiedJarRepository.Entry("play-services-ads-identifier", "18.0.1"),
            parser.parse("~/.gradle/caches/transforms-3/ee61fea61f93c586b4a5c9757532036a/transformed/jetified-play-services-ads-identifier-18.0.1-api.aar"),
        )
        // jetified
        assertEquals(
            JetifiedJarRepository.Entry("kotlin-stdlib", "1.5.31"),
            parser.parse("~/.gradle/caches/transforms-3/624ba37502a20fb5cf6bcbefb0eca46d/transformed/jetified-kotlin-stdlib-1.5.31.aar"),
        )
        // api
        assertEquals(
            JetifiedJarRepository.Entry("ads-identifier", "1.0.0-alpha03"),
            parser.parse("~/.gradle/caches/transforms-3/1665e7f6142dc066b4a4db113e2e8cdc/transformed/ads-identifier-1.0.0-alpha03-api.aar"),
        )
        // plain
        assertEquals(
            JetifiedJarRepository.Entry("media", "1.4.1"),
            parser.parse("~/.gradle/caches/transforms-3/674bb7a92157d9ee02bd120f5791f911/transformed/media-1.4.1.aar"),
        )
    }

    @Test
    fun `Test JARS resources inside jars directory`() {
        // jetified, api
        assertEquals(
            JetifiedJarRepository.Entry("core-icons-generated-25", "1.54.4-oldlibverify-SNAPSHOT"),
            parser.parse("~/.gradle/caches/transforms-3/cf8cbc4e53bf5a40c0ed6af5c89d54ec/transformed/jetified-core-icons-generated-25-1.54.4-oldlibverify-SNAPSHOT-api/jars/classes.jar"),
        )
        // jetified
        assertEquals(
            JetifiedJarRepository.Entry("core-icons-generated-25", "1.54.4-oldlibverify-SNAPSHOT"),
            parser.parse("~/.gradle/caches/transforms-3/cf8cbc4e53bf5a40c0ed6af5c89d54ec/transformed/jetified-core-icons-generated-25-1.54.4-oldlibverify-SNAPSHOT/jars/classes.jar"),
        )
        // api
        assertEquals(
            JetifiedJarRepository.Entry("core-icons-generated-25", "1.54.4-oldlibverify-SNAPSHOT"),
            parser.parse("~/.gradle/caches/transforms-3/cf8cbc4e53bf5a40c0ed6af5c89d54ec/transformed/core-icons-generated-25-1.54.4-oldlibverify-SNAPSHOT-api/jars/classes.jar"),
        )
        // plain
        assertEquals(
            JetifiedJarRepository.Entry("core-icons-generated-25", "1.54.4-oldlibverify-SNAPSHOT"),
            parser.parse("~/.gradle/caches/transforms-3/cf8cbc4e53bf5a40c0ed6af5c89d54ec/transformed/core-icons-generated-25-1.54.4-oldlibverify-SNAPSHOT/jars/classes.jar"),
        )
    }

    @Test
    fun `Test strange artifact version`() {
        // jetified, api
        assertEquals(
            JetifiedJarRepository.Entry("cameraview", "5b2f0fff93"),
            parser.parse("~/.gradle/caches/transforms-3/1c66649054b85a1e4c2801d896eca713/transformed/jetified-cameraview-5b2f0fff93-api.aar"),
        )
        // jetified
        assertEquals(
            JetifiedJarRepository.Entry("cameraview", "5b2f0fff93"),
            parser.parse("~/.gradle/caches/transforms-3/1c66649054b85a1e4c2801d896eca713/transformed/jetified-cameraview-5b2f0fff93.aar"),
        )
        // api
        assertEquals(
            JetifiedJarRepository.Entry("cameraview", "5b2f0fff93"),
            parser.parse("~/.gradle/caches/transforms-3/1c66649054b85a1e4c2801d896eca713/transformed/cameraview-5b2f0fff93-api.aar"),
        )
        // plain
        assertEquals(
            JetifiedJarRepository.Entry("cameraview", "5b2f0fff93"),
            parser.parse("~/.gradle/caches/transforms-3/1c66649054b85a1e4c2801d896eca713/transformed/cameraview-5b2f0fff93.aar"),
        )
    }
}