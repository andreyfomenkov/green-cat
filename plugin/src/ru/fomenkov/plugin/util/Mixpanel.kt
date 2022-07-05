package ru.fomenkov.plugin.util

import com.mixpanel.mixpanelapi.ClientDelivery
import com.mixpanel.mixpanelapi.MessageBuilder
import com.mixpanel.mixpanelapi.MixpanelAPI
import org.json.JSONObject
import java.io.IOException

object Mixpanel {

    // Token
    private const val PROJECT_TOKEN = "22312ddd82e4d491f6d082f80518fed1"

    // Events
    private const val EVENT_LAUNCH = "launch"
    private const val EVENT_COMPLETE = "complete"
    private const val EVENT_FAILED = "failed"

    // Keys
    private const val KEY_DURATION = "duration"
    private const val KEY_MESSAGE = "message"

    private val messageBuilder = MessageBuilder(PROJECT_TOKEN)
    private val mixpanel = MixpanelAPI()

    fun launch() {
        deliver(EVENT_LAUNCH)
    }

    fun complete(duration: Long) {
        val props = JSONObject().apply {
            put(KEY_DURATION, duration)
        }
        deliver(EVENT_COMPLETE, props)
    }

    fun failed(message: String) {
        val props = JSONObject().apply {
            put(KEY_MESSAGE, message)
        }
        deliver(EVENT_FAILED, props)
    }

    private fun deliver(eventName: String, props: JSONObject? = null) {
        try {
            val delivery = ClientDelivery()
            val message = messageBuilder.event(DISTINCT_ID, eventName, props)
            delivery.addMessage(message)
            mixpanel.deliver(delivery)
        } catch (error: IOException) {
            Telemetry.err("Failed to deliver event: ${error.message}")
        }
    }
}