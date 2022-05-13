package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.data.RepositoryResource
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.timeMillis
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.jar.JarFile

class JetifiedJarRepository : JarRepository() {

    override fun scan() {
        val cpus = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(cpus)

        val time = timeMillis {
            val files = File("/Users/andrey.fomenkov/.gradle/caches/transforms-3").listFiles()!! // TODO
            val latch = CountDownLatch(files.size)
            val output = ConcurrentHashMap<String, RepositoryResource.JarResource>()

            files.forEach { dir ->
                executor.submit {
                    val transformedDir = File(dir, "transformed")

                    if (transformedDir.exists()) {
                        val jar = transformedDir.listFiles { file -> file.extension == "jar" }!!.firstOrNull() // TODO

                        if (jar != null) {
                            val entries = JarFile(jar.absolutePath).entries().toList()

                            entries.forEach { entry ->
                                val packageName = getPackageName(entry)

                                if (packageName != null) {
                                    val resource = RepositoryResource.JarResource(
                                        packageName = packageName,
                                        jarFilePath = jar.absolutePath
                                    )
                                    output += packageName to resource
                                }
                            }
                        }
                    }
                    latch.countDown()
                }
            }
            latch.await()
            executor.shutdown()
            output.forEach(this::add)
        }
        Telemetry.log("Scan jetified JAR files: $time ms")
    }
}