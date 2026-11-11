/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.sms

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldProblem
import io.brulejr.groupnotify.plugin.FieldType
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginField
import io.brulejr.groupnotify.plugin.PluginStatus

/**
 * Text messages through Twilio's Programmable Messaging API. Each number is sent its own
 * message, so recipients never see one another.
 *
 * Settings (`notify.delivery.settings.sms.*`):
 * - `account-sid` and `auth-token`: required, from the Twilio console. An API key works
 *   too: put its SID in `api-key-sid` and its secret in `auth-token`.
 * - `from`: the sending number in E.164, or `messaging-service-sid` instead.
 * - `max-length`: characters before the text is cut, default 1600 (Twilio's limit).
 * - `api-base-url`: default `https://api.twilio.com`; for tests.
 * - `timeout`: seconds to wait for Twilio, default 10.
 */
class SmsPlugin : DeliveryPlugin {

    private lateinit var context: PluginContext
    private var client: TwilioClient? = null
    private var status = PluginStatus.notReady("not started")
    private var maxLength = TWILIO_LIMIT

    override val id = "sms"
    override val name = "SMS"
    override val description = "Sends a text message to each phone number, through Twilio."
    override val icon = "phone"

    override val fields = listOf(
        PluginField(
            "to", "Phone numbers", FieldType.PHONE, required = true,
            placeholder = "+15551234567, +15557654321", help = "E.164, with the country code. One message is sent to each.",
        ),
        PluginField("prefix", "Prefix", placeholder = "e.g. [Club]", help = "Starts every message, so recipients know who it is from."),
        PluginField("includeSubject", "Start with the subject", FieldType.CHECKBOX, defaultValue = "true"),
    )

    override fun start(context: PluginContext) {
        this.context = context
        val settings = context.settings
        maxLength = settings["max-length"]?.toIntOrNull()?.coerceIn(70, TWILIO_LIMIT) ?: TWILIO_LIMIT
        status = PluginStatus.requireSettings(settings, PREFIX, "account-sid", "auth-token")
        if (status.ready && settings["from"] == null && settings["messaging-service-sid"] == null) {
            status = PluginStatus.notReady("missing $PREFIX.from or $PREFIX.messaging-service-sid")
        }
        if (status.ready) client = TwilioClient(context, settings)
    }

    override fun status() = status

    override fun validate(target: DeliveryTarget): List<FieldProblem> =
        target.list("to").filterNot { E164.matches(it) }.map { FieldProblem("to", "$it is not in E.164 form, e.g. +15551234567") }

    override fun summarize(target: DeliveryTarget): String {
        val numbers = target.list("to")
        return if (numbers.size == 1) mask(numbers.single()) else "${numbers.size} numbers"
    }

    override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult {
        val client = this.client ?: return DeliveryResult.Failed("SMS is not configured: ${status.reason}")
        val body = SmsText.compose(message, target["prefix"], target["includeSubject"]?.toBooleanStrictOrNull() ?: true, maxLength)
        val numbers = target.list("to")
        val outcomes = numbers.associateWith { client.send(it, body) }
        val failed = outcomes.filterValues { it is DeliveryResult.Failed }.mapValues { it.value as DeliveryResult.Failed }
        return when {
            failed.isEmpty() -> DeliveryResult.Delivered(
                "${numbers.size} of ${numbers.size} numbers",
                outcomes.values.mapNotNull { (it as DeliveryResult.Delivered).reference }.joinToString(",").ifEmpty { null },
            )
            // Retried only when nothing went: a retry after a partial send would text some
            // people twice.
            failed.size == numbers.size -> DeliveryResult.Failed(
                failed.values.map { it.reason }.distinct().joinToString("; "),
                retryable = failed.values.all { it.retryable },
            )
            else -> DeliveryResult.Failed(
                "sent to ${numbers.size - failed.size} of ${numbers.size} numbers; " +
                    failed.entries.joinToString("; ") { (number, f) -> "${mask(number)}: ${f.reason}" },
            )
        }
    }

    private companion object {
        const val PREFIX = "notify.delivery.settings.sms"
        const val TWILIO_LIMIT = 1600
        val E164 = Regex("\\+[1-9]\\d{6,14}")

        fun mask(number: String) = if (number.length <= 4) number else number.take(2) + "•••" + number.takeLast(4)
    }
}
