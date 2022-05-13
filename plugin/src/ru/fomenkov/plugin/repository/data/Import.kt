package ru.fomenkov.plugin.repository.data

data class Import(
    private val packageName: String,
    val isStatic: Boolean,
    val hasTrailingWildcard: Boolean,
) {
    fun parts() = packageName.split(".")

    fun packageName() = if (isStatic) {
        val parts = parts().dropLast(1)
        parts.joinToString(separator = ".")
    } else {
        packageName
    }
}