package ru.fomenkov.plugin.repository

import ru.fomenkov.plugin.repository.data.JarResource
import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.exec
import ru.fomenkov.plugin.util.timeMillis
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.jar.JarEntry
import java.util.jar.JarFile

abstract class JarRepository : ResourceRepository<JarResource>() {

    protected fun getPackageName(entry: JarEntry): String? {
        val str = entry.toString()

        return if (str.endsWith(".class")) {
            val endIndex = str.length - 6
            str.substring(0, endIndex).replace('/', '.')
        } else {
            null
        }
    }
}