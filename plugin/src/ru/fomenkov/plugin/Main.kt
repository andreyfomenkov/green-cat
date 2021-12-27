package ru.fomenkov.plugin

import ru.fomenkov.plugin.task.Result
import ru.fomenkov.plugin.task.resolve.GradleProjectInput
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.util.Telemetry

fun main(args: Array<String>) {
    Telemetry.isVerbose = true

    val result = ProjectResolveTask(
        GradleProjectInput(
            propertiesFileName = "gradle.properties",
            settingsFileName = "settings.gradle",
        )
    ).run()

    if (result is Result.Error) {
        Telemetry.err("Error: ${result.error.localizedMessage}")
    }
}