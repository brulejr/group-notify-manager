/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin.testing

import io.brulejr.groupnotify.plugin.NotificationMessage
import java.time.Instant

/** A notification as the application would hand it to a plugin. */
object TestMessages {
    fun meeting(markdown: String = "The **monthly meeting** is on Tuesday.") = NotificationMessage(
        notificationId = "6f1c2c7e-0000-4000-8000-000000000001",
        title = "Monthly meeting",
        group = "club_members",
        subject = "Meeting reminder",
        markdown = markdown,
        html = "<p>The <strong>monthly meeting</strong> is on Tuesday.</p>",
        text = "The monthly meeting is on Tuesday.",
        scheduledFor = Instant.parse("2026-11-10T18:00:00Z"),
    )
}
