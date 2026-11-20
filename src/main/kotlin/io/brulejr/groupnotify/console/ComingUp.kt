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
 * One thing about to be sent, as the dashboard lists it: a notification's next run, or
 * an event's next reminder.
 *
 * @property label what kind it is: a notification's schedule kind, e.g. `Recurring`, or
 *   an event's reminder, e.g. `1 hour before`.
 * @property href its page.
 */
data class ComingUp(
    val at: Instant,
    val zone: ZoneId,
    val title: String,
    val subject: String,
    val group: String,
    val label: String,
    val href: String,
    val event: Boolean,
)
