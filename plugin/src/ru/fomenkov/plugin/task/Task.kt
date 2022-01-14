package ru.fomenkov.plugin.task

import ru.fomenkov.plugin.util.Telemetry
import ru.fomenkov.plugin.util.formatMillis

abstract class Task<I, O>(private val input: I) {

    abstract fun body(): O

    fun run() = try {
        measureTime { Result.Complete(body()) }
    } catch (error: Throwable) {
        Result.Error(error)
    }

    private fun measureTime(task: () -> Result.Complete<O>): Result.Complete<O> {
        Telemetry.log("[${javaClass.simpleName}] Started")
        val startTime = System.currentTimeMillis()
        val result = task()
        val endTime = System.currentTimeMillis()
        Telemetry.log("[${javaClass.simpleName}] Complete in ${formatMillis(endTime - startTime)}\n")
        return result
    }
}

sealed class Result<T> {
    data class Complete<T>(val output: T) : Result<T>()
    data class Error(val error: Throwable) : Result<Nothing>()
}