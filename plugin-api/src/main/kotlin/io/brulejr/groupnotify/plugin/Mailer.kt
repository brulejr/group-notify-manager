/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

/** Sends email through the application's SMTP connection. */
interface Mailer {

    /** The address mail is sent from unless a message says otherwise. */
    val defaultFrom: String

    /** Sends [message], throwing if the SMTP server refuses it. */
    fun send(message: MailMessage)
}
