package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.data.JarResource
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
            val dirs = File("/Users/andrey.fomenkov/.gradle/caches/modules-2/files-2.1") // TODO
                .listFiles()!!
                .filter { file -> file.isDirectory }
            val latch = CountDownLatch(dirs.size)
            val output = ConcurrentHashMap<String, JarResource>()

            dirs.forEach { dir ->
                executor.submit {
                    val jars = exec("find ${dir.absolutePath} -name '*.jar'")
                        .filterNot { path -> path.endsWith("-sources.jar") }
                        .filterNot { path -> path.endsWith("-javadoc.jar") }

                    jars.forEach { path ->
                        val entries = JarFile(path).entries().toList()

                        entries.forEach { entry ->
                            val packageName = getPackageName(entry)

                            if (packageName != null) {
                                val resource = JarResource(packageName = packageName, jarFilePath = path)
                                output += packageName to resource
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
        Telemetry.log("Scan support JAR files: $time ms")
    }
}