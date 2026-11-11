/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldOption
import io.brulejr.groupnotify.plugin.FieldType
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginField
import io.brulejr.groupnotify.plugin.PluginLog

/**
 * Writes the notification to the application log instead of sending it: for trying out
 * a schedule, or a group that has no channel yet. Built in, and declared as a bean
 * rather than found by ServiceLoader, which is the other way a plugin can be provided.
 */
class LogPlugin : DeliveryPlugin {

    private lateinit var log: PluginLog

    override val id = "log"
    override val name = "Log only"
    override val description = "Writes the notification to the application log. Sends nothing; for testing schedules."
    override val icon = "journal-text"

    override val fields = listOf(
        PluginField(
            "body", "Write", FieldType.SELECT,
            options = listOf(FieldOption("subject", "Subject only"), FieldOption("text", "Subject and text")),
            defaultValue = "subject",
        ),
    )

    override fun start(context: PluginContext) {
        log = context.log
    }

    override fun summarize(target: DeliveryTarget) = "application log"

    override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult {
        val text = if (target["body"] == "text") "\n" + message.text else ""
        log.info("[${message.group}] ${message.subject}$text")
        return DeliveryResult.Delivered("logged")
    }
}
