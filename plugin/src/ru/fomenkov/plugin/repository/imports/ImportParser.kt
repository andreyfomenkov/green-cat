package ru.fomenkov.plugin.repository.imports

import java.lang.Integer.min

class ImportParser {

    fun parse(line: String): Import? {
        if (line.isBlank()) {
            return null
        }
        val parts = line.trim().split(" ").filter(String::isNotBlank)
        var isStatic = false
        var hasTrailingWildcard = false

        if (parts.getOrNull(0) != "import") {
            return null
        }
        var packageName = if (parts.getOrNull(1) == "static") {
            isStatic = true
            parts.getOrNull(2) ?: ""
        } else {
            parts.getOrNull(1) ?: ""
        }
        if (packageName.isBlank()) {
            return null
        }
        val commentIndex = packageName.indexOf("//")
        val semicolonIndex = packageName.indexOf(';')

        when {
            commentIndex != -1 && semicolonIndex != -1 -> {
                packageName = packageName.substring(0, min(commentIndex, semicolonIndex))
            }
            commentIndex != -1 -> {
                packageName = packageName.substring(0, commentIndex)
            }
            semicolonIndex != -1 -> {
                packageName = packageName.substring(0, semicolonIndex)
            }
        }
        if (packageName.endsWith(".*")) {
            packageName = packageName.substring(0, packageName.length - 2)
            hasTrailingWildcard = true
        }
        return Import(packageName, isStatic = isStatic, hasTrailingWildcard = hasTrailingWildcard)
    }
}