package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.parser.JetifiedResourceParser
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.noTilda
import ru.fomenkov.plugin.util.timeMillis
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class JetifiedJarRepository(
    private val parser: JetifiedResourceParser,
) : JarRepository() {

    private val cacheDir = "~/.gradle/caches/transforms-3".noTilda() // TODO: search between transforms-X
    private val artifactVersions = mutableMapOf<String, Set<String>>() // artifact ID -> available versions
    private val artifactPaths = mutableMapOf<Entry, Set<String>>() // artifact entry -> available JARs and AARs

    init {
        if (!File(cacheDir).exists()) {
            error("Gradle cache path doesn't exist: $cacheDir")
        }
    }

    fun getAvailableVersions(artifactId: String) = artifactVersions[artifactId] ?: emptySet()

    fun getArtifactPaths(artifactId: String, version: String): Set<String> {
        val entry = Entry(artifactId, version)
        return artifactPaths[entry] ?: emptySet()
    }

    override fun scan() {
        val cpus = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(cpus)
        val allResources = mutableSetOf<String>()

        val time = timeMillis {
            val files = File(cacheDir).listFiles()!! // TODO
            val latch = CountDownLatch(files.size)

            files.forEach { dir ->
                executor.submit {
                    val dirResources = mutableSetOf<String>()
                    val transformedDir = File(dir, "transformed")
                    val dirScanTask = { dir: File ->
                        if (dir.exists()) {
                            (dir.list() ?: emptyArray()).forEach { path ->
                                if (path.endsWith(".jar")) {
                                    dirResources += File(dir, path).absolutePath
                                }
                            }
                        }
                    }
                    if (transformedDir.exists()) {
                        (transformedDir.list() ?: emptyArray()).forEach { resPath ->
                            if (resPath.endsWith(".jar") || resPath.endsWith(".aar")) {
                                dirResources += File(transformedDir, resPath).absolutePath
                            } else {
                                val jarsDir = File(transformedDir, "$resPath/jars")
                                val libsDir = File(jarsDir, "libs")
                                dirScanTask(jarsDir)
                                dirScanTask(libsDir)
                            }
                        }
                    }
                    synchronized(allResources) { allResources += dirResources }
                    latch.countDown()
                }
            }
            latch.await()
            executor.shutdown()
            parseArtifactPaths(allResources)
        }
        Telemetry.log("Scan jetified JAR files: $time ms")
    }

    private fun parseArtifactPaths(allPaths: Set<String>) {
        allPaths.forEach { path ->
            val entry = parser.parse(path)
            val versions = artifactVersions[entry.artifactId] ?: mutableSetOf()
            val paths = artifactPaths[entry] ?: emptySet()

            artifactVersions[entry.artifactId] = versions + entry.version
            artifactPaths[entry] = paths + path
        }
    }

    data class Entry(val artifactId: String, val version: String)
}