package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.data.RepositoryResource
import ru.fomenkov.plugin.resolver.ProjectResolver
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.timeMillis
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ClassFileRepository : ResourceRepository<RepositoryResource.ClassResource>() {

    private val buildDirs = listOf( // TODO
        "build/intermediates/javac/debug/classes",
        "build/classes/java/main",
        "build/classes/kotlin/main",
        "build/tmp/kotlin-classes/debug",
        "build/tmp/kotlin-classes/main",
    )

    override fun scan() {
        val time = timeMillis {
            val resolver = ProjectResolver(
                propertiesFileName = "gradle.properties", // TODO
                settingsFileName = "settings.gradle",
                ignoredModules = emptySet(),
                ignoredLibs = emptySet(),
            )
            val declarations = resolver.parseModuleDeclarations()
            val tasksCount = declarations.size * buildDirs.size
            val countDownLatch = CountDownLatch(tasksCount)
            val cpus = Runtime.getRuntime().availableProcessors()
            val executor = Executors.newFixedThreadPool(cpus)
            val output = ConcurrentHashMap<String, RepositoryResource.ClassResource>()

            declarations.forEach { (name, path) ->
                buildDirs.forEach { buildDir ->
                    executor.submit {
                        scanBuildDirectory("$path/$buildDir", output)
                        countDownLatch.countDown()
                    }
                }
            }
            countDownLatch.await()
            executor.shutdown()
            output.forEach(this::add)
        }
        Telemetry.log("Scan class files: $time ms")
    }

    private fun scanBuildDirectory(moduleBuildPath: String, output: MutableMap<String, RepositoryResource.ClassResource>) {
        if (!File(moduleBuildPath).exists()) {
            return
        }
        val classFilePaths = exec("find $moduleBuildPath -name '*.class'")
            .filterNot { path -> path.contains("$") } // TODO: need?

        classFilePaths.forEach { path ->
            val packageName = getPackageName(moduleBuildPath, path)
            val resource = RepositoryResource.ClassResource(
                packageName = packageName,
                classFilePath = path,
                buildDirPath = moduleBuildPath,
            )
            output += packageName to resource
        }
    }

    private fun getPackageName(moduleBuildPath: String, classFilePath: String): String {
        val startIndex = moduleBuildPath.length + 1
        val endIndex = classFilePath.length - 6
        return classFilePath.substring(startIndex, endIndex)
            .replace('/', '.')
    }
}