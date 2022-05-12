package ru.fomenkov.plugin.util

import java.lang.IllegalArgumentException

object PackageNameUtil {

    fun split(packageName: String, ignoreLast: Int = 0): List<String> {
        if (packageName.isBlank()) {
            return emptyList()
        }
        val parts = packageName.split(".")

        if (parts.size <= ignoreLast || ignoreLast < 0) {
            throw IllegalArgumentException("Incorrect ignoreLast value")
        }
        return parts.subList(fromIndex = 0, toIndex = parts.size - ignoreLast)
    }
}