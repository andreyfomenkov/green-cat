package ru.fomenkov.plugin

import ru.fomenkov.plugin.task.Result
import ru.fomenkov.plugin.task.resolve.GradleProjectInput
import ru.fomenkov.plugin.task.resolve.ProjectResolveTask
import ru.fomenkov.plugin.util.Telemetry

fun main(args: Array<String>) {
    Telemetry.isVerbose = false

    val result = ProjectResolveTask(
        GradleProjectInput(
            propertiesFileName = "gradle.properties",
            settingsFileName = "settings.gradle",
        )
    ).run()

    when (result) {
        is Result.Complete -> Telemetry.log("* * * GreenCat: OK * * *")
        is Result.Error -> Telemetry.err("* * * GreenCat FAIL: ${result.error.localizedMessage} * * *")
    }
}