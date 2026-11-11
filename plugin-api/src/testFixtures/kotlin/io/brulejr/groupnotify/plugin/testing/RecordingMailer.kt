/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin.testing

import io.brulejr.groupnotify.plugin.MailMessage
import io.brulejr.groupnotify.plugin.Mailer

/** Keeps every message instead of sending it, or throws [failWith] if set. */
class RecordingMailer(override val defaultFrom: String = "notify@example.org") : Mailer {

    val sent = mutableListOf<MailMessage>()

    var failWith: Exception? = null

    override fun send(message: MailMessage) {
        failWith?.let { throw it }
        sent += message
    }
}
