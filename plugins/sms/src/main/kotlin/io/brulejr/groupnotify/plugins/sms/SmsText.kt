/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.sms

import io.brulejr.groupnotify.plugin.NotificationMessage

/** The text of one message: the prefix, the subject, then the body, cut to fit. */
object SmsText {

    fun compose(message: NotificationMessage, prefix: String?, includeSubject: Boolean, maxLength: Int): String {
        val head = listOfNotNull(prefix?.trim()?.takeIf { it.isNotEmpty() }, message.subject.takeIf { includeSubject })
            .joinToString(" ")
        // Blank lines matter less on a phone than characters do.
        val body = message.text.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }.joinToString("\n")
        val text = listOf(head, body).filter { it.isNotEmpty() }.joinToString("\n")
        return if (text.length <= maxLength) text else text.take(maxLength - 1) + "…"
    }
}
