package ru.fomenkov.plugin.task

abstract class Task<I, O>(private val input: I) {

    abstract fun body(): O

    fun run() = try {
        Result.Complete(body())
    } catch (error: Throwable) {
        Result.Error(error)
    }
}

sealed class Result<T> {
    data class Complete<T>(val output: T) : Result<T>()
    data class Error(val error: Throwable) : Result<Nothing>()
}