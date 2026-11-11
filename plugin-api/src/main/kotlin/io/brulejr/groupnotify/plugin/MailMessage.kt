/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

/**
 * One email. Sent as multipart text and HTML when [html] is given, plain text otherwise.
 *
 * @property from null for the [Mailer.defaultFrom].
 */
data class MailMessage(
    val to: List<String>,
    val subject: String,
    val text: String,
    val html: String? = null,
    val from: String? = null,
    val replyTo: String? = null,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
) {
    init {
        require(to.isNotEmpty() || bcc.isNotEmpty()) { "an email needs a recipient" }
    }
}
