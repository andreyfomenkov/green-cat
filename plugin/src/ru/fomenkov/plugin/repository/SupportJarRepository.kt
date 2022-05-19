package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.data.RepositoryResource
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.timeMillis
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.jar.JarFile

class SupportJarRepository : JarRepository() {

    override fun scan() {
        val cpus = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(cpus)

        val time = timeMillis {
            val files = File("/Users/andrey.fomenkov/.gradle/caches/modules-2/files-2.1").listFiles() // TODO
            val dirs = checkNotNull(files) { "No files listed" }.filter { file -> file.isDirectory }
            val latch = CountDownLatch(dirs.size)
            val jarsOutput = ConcurrentHashMap<String, RepositoryResource.JarResource>()

            dirs.forEach { dir ->
                executor.submit {
                    val resources = exec("find ${dir.absolutePath}")
                    val jars = resources
                        .filter { path -> path.endsWith(".jar") }
                        .filterNot { path -> path.endsWith("-sources.jar") }
                        .filterNot { path -> path.endsWith("-javadoc.jar") }

                    jars.forEach { path ->
                        val entries = JarFile(path).entries().toList()

                        entries.forEach { entry ->
                            val packageName = getPackageName(entry)

                            if (packageName != null) {
                                val resource = RepositoryResource.JarResource(packageName = packageName, jarFilePath = path)
                                jarsOutput += packageName to resource
                            }
                        }
                    }
                    latch.countDown()
                }
            }
            latch.await()
            executor.shutdown()
            jarsOutput.forEach(this::add)
        }
        Telemetry.log("Scan support JAR files: $time ms")
    }
}