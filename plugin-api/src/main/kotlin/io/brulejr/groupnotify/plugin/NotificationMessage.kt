/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

import java.time.Instant

/**
 * One notification, ready to send, in every form a plugin might want. The application
 * renders all of them from whichever the author wrote, Markdown or HTML, and has already
 * filled in placeholders such as `{{date}}`.
 *
 * @property subject a single line: an email subject, an embed title.
 * @property markdown the body as Markdown, for Discord and the like.
 * @property html the body as sanitised HTML, safe to put in an email.
 * @property text the body as plain text, for radio and other text-only channels.
 * @property group the OIDC group the notification belongs to.
 * @property scheduledFor when it was due; for a manual send, when it was asked for.
 */
data class NotificationMessage(
    val notificationId: String,
    val title: String,
    val group: String,
    val subject: String,
    val markdown: String,
    val html: String,
    val text: String,
    val scheduledFor: Instant,
)
