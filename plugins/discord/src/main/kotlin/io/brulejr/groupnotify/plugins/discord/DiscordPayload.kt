/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.discord

import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.NotificationMessage

/**
 * The webhook's JSON body. Discord limits a message to 2000 characters, an embed title
 * to 256 and its description to 4096, so longer text is cut with an ellipsis rather than
 * refused.
 */
object DiscordPayload {

    const val CONTENT_LIMIT = 2000
    const val TITLE_LIMIT = 256
    const val DESCRIPTION_LIMIT = 4096

    fun build(message: NotificationMessage, target: DeliveryTarget, mention: Mention?): Map<String, Any?> {
        val plain = target["style"] == "plain"
        val body = linkedMapOf<String, Any?>()
        target["username"]?.let { body["username"] = it }
        target["avatarUrl"]?.let { body["avatar_url"] = it }
        if (plain) {
            val text = listOfNotNull(mention?.content, "**${message.subject}**", message.markdown).joinToString("\n\n")
            body["content"] = cut(text, CONTENT_LIMIT)
        } else {
            mention?.let { body["content"] = it.content }
            body["embeds"] = listOf(
                mapOf(
                    "title" to cut(message.subject, TITLE_LIMIT),
                    "description" to cut(message.markdown, DESCRIPTION_LIMIT),
                    "timestamp" to message.scheduledFor.toString(),
                    "footer" to mapOf("text" to message.group),
                ),
            )
        }
        // Nothing pings unless asked: without this, text that happens to contain @everyone would.
        body["allowed_mentions"] = mapOf("parse" to (mention?.parse ?: emptyList()), "roles" to (mention?.roles ?: emptyList()))
        return body
    }

    fun cut(text: String, limit: Int): String = if (text.length <= limit) text else text.take(limit - 1) + "…"
}
