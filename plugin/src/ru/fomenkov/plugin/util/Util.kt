package ru.fomenkov.plugin.util

import java.io.File

val CURRENT_DIR: String = File("").absolutePath
val HOME_DIR: String = exec("echo ~").firstOrNull().let { path ->
    when {
        path.isNullOrBlank() -> error("Failed to get home directory")
        else -> path
    }
}

fun formatMillis(value: Long) = when {
    value < 1000 -> "$value ms"
    else -> "${"%.1f".format(value / 1000f)} sec".replace(",", ".")
}

fun String.isVersionGreaterOrEquals(version: String) = this >= version

fun timeMillis(action: () -> Unit): Long {
    val start = System.currentTimeMillis()
    action()
    val end = System.currentTimeMillis()
    return end - start
}