package ru.fomenkov.runner.ssh

import ru.fomenkov.plugin.util.exec
import ru.fomenkov.runner.logger.Log

class SshCommandSequenceBuilder {

    private val sequence = StringBuilder()

    fun cmd(command: String) {
        sequence.append("$command\n")
    }

    fun build() = sequence.toString()
}

private var sshHost = ""

fun setRemoteHost(host: String) {
    sshHost = host
}

fun ssh(print: Boolean = false, sequence: SshCommandSequenceBuilder.() -> Unit): List<String> {
    check(sshHost.isNotBlank()) { "SSH host is not specified" }
    val builder = SshCommandSequenceBuilder()
    sequence(builder)
    return exec("ssh -T $sshHost << EOF\n${builder.build()}\nEOF", print)
        .also { lines ->
            val hasError = lines.find { line -> line.lowercase().contains("could not resolve hostname") } != null

            if (hasError) {
                error("Could not resolve hostname. Is everything fine with your VPN?")
            }
        }
}