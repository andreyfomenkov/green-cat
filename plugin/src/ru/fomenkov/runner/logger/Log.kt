package ru.fomenkov.runner.logger

object Log {

    fun d(message: String, newLine: Boolean = true) {
        if (newLine) {
            println(message)
        } else {
            print(message)
        }
    }

    fun e(message: String) = System.err.println(message)
}