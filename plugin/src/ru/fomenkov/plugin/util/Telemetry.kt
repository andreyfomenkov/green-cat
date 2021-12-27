package ru.fomenkov.plugin.util


object Telemetry {

    var isVerbose = false

    fun verboseLog(message: String) {
        if (isVerbose) println(message)
    }

    fun verboseErr(message: String) {
        if (isVerbose) System.err.println(message)
    }

    fun log(message: String) = println(message)

    fun err(message: String) = System.err.println(message)
}