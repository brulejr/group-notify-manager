/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console

import java.time.Instant
import java.time.ZoneId

/**
 * An event's next occurrence, as the dashboard lists it.
 *
 * @property nextReminder e.g. `1 hour before`, the reminder due next; null when none is.
 */
data class NextEvent(
    val at: Instant,
    val zone: ZoneId,
    val title: String,
    val group: String,
    val href: String,
    val nextReminder: String?,
    val nextReminderAt: Instant?,
)
