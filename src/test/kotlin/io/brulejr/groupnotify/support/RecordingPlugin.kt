/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.support

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldType
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginField

/**
 * A plugin that keeps what it is asked to send, and answers with each of [results] in
 * turn, then the last one forever.
 */
class RecordingPlugin(override val id: String = "recording", vararg results: DeliveryResult) : DeliveryPlugin {

    private val queue = ArrayDeque(results.toList().ifEmpty { listOf(DeliveryResult.Delivered("ok")) })

    val sent = mutableListOf<Pair<NotificationMessage, DeliveryTarget>>()

    override val name = "Recording"
    override val description = "Keeps what it is sent."

    override val fields = listOf(
        PluginField("channel", "Channel", required = true),
        PluginField("token", "Token", secret = true),
        PluginField("loud", "Loud", FieldType.CHECKBOX),
    )

    override fun summarize(target: DeliveryTarget) = "#${target["channel"]}"

    @Synchronized
    override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult {
        sent += message to target
        return if (queue.size > 1) queue.removeFirst() else queue.first()
    }
}
