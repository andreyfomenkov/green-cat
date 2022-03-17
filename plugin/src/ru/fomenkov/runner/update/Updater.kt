package ru.fomenkov.runner.update

import ru.fomenkov.runner.params.RunnerParams

interface Updater {

    fun isNeedToCheckVersion(): Boolean

    fun checkForUpdate(params: RunnerParams, forceCheck: Boolean)
}