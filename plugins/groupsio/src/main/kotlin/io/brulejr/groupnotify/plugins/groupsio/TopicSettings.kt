/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.groupsio

import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldOption

/**
 * What a delivery asks of the topic its post starts, applied with `updatetopic` once the
 * post is in the archive. Each is an owner's or moderator's setting (`edit_archives`).
 */
data class TopicSettings(
    val sticky: Boolean = false,
    val locked: Boolean = false,
    val moderated: Boolean = false,
    val replyTo: String? = null,
) {

    val any get() = sticky || locked || moderated || replyTo != null

    /** The `updatetopic` parameters, beside `topic_id`. */
    fun form(): Map<String, String> = buildMap {
        if (sticky) put("is_sticky", "true")
        if (locked) put("is_closed", "true")
        if (moderated) put("is_moderated", "true")
        replyTo?.let { put("reply_to", it) }
    }

    /** For the history, e.g. `pinned, locked, replies to the moderators`. */
    fun describe(): String = listOfNotNull(
        "pinned".takeIf { sticky },
        "locked".takeIf { locked },
        "replies moderated".takeIf { moderated },
        replyTo?.let { value -> "replies " + REPLY_TO.first { it.value == value }.label.replaceFirstChar(Char::lowercase) },
    ).joinToString(", ")

    companion object {
        /** groups.io's choices, less its group default, which is what leaving it blank means. */
        val REPLY_TO = listOf(
            FieldOption("thread_reply_to_group", "To the group"),
            FieldOption("thread_reply_to_sender", "To the sender"),
            FieldOption("thread_reply_to_group_and_sender", "To the group and the sender"),
            FieldOption("thread_reply_only_to_sender", "Only to the sender"),
            FieldOption("thread_reply_to_moderators", "To the moderators"),
        )

        fun of(target: DeliveryTarget) = TopicSettings(
            sticky = target.flag("sticky"),
            locked = target.flag("locked"),
            moderated = target.flag("moderated"),
            replyTo = target["replyTo"]?.trim(),
        )
    }
}
