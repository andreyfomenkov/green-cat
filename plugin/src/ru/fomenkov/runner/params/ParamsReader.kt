package ru.fomenkov.runner.params

import ru.fomenkov.runner.logger.Log

class ParamsReader(
    private val args: Array<String>,
) {
    private val keysMap = Param.values().associateBy { param -> param.key }

    fun read(): RunnerParams {
        val paramsMap = mutableMapOf<Param, String>()
        fun value(param: Param) = checkNotNull(paramsMap[param]) { "Missing parameter ${param.key}" }

        for (i in args.indices step 2) {
            val param = parseParam(args[i])

            if (i == args.size - 1) {
                error("No expected value for parameter ${param.key}")
            }
            val value = args[i + 1]
            check(value.isNotBlank()) { "Empty value for parameter ${param.key}" }
            paramsMap += param to value
        }
        val mode = when (val name = value(Param.RUNNER_MODE)) {
            Mode.DEBUG.mode -> RunnerMode.Debug(
                componentName = value(Param.COMPONENT_NAME),
            )
            Mode.UITEST.mode -> RunnerMode.UiTest(
                testClass = value(Param.TEST_CLASS),
                testRunner = value(Param.TEST_RUNNER),
            )
            else -> error("Unknown runner mode: $name")
        }
        return RunnerParams(
            sshHost = value(Param.SSH_HOST),
            projectRoot = value(Param.PROJECT_ROOT),
            androidSdkRoot = value(Param.ANDROID_SDK_ROOT),
            mode = mode,
        )
    }

    private fun parseParam(value: String) = checkNotNull(keysMap[value]) { "Unknown parameter: $value" }

    companion object {

        fun displayHelp() {
            Log.d("Usage:\n")

            Param.values().forEach { param ->
                Log.d("   ${param.key}   ${param.description}")
            }
        }
    }
}