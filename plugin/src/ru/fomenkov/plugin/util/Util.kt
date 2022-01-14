package ru.fomenkov.plugin.util

fun formatMillis(value: Long) = when {
    value < 1000 -> "$value ms"
    else -> "${"%.1f".format(value / 1000f)} sec".replace(",", ".")
}

fun String.isVersionGreaterOrEquals(version: String) = this >= version