package ru.fomenkov.runner.logger

object Log {

    fun d(message: String) = println(message)

    fun e(message: String) = System.err.println(message)
}