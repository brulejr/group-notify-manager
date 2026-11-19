/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import java.time.Instant
import java.util.UUID

/**
 * The next reminder an event will send: when, for which occurrence, and which reminder.
 * Two reminders of different occurrences may fall at the same [sendAt], e.g. a weekly
 * event's "1 week before" and "at the start"; [occurrence] and the reminder's place in
 * the event order them.
 */
data class PendingReminder(val sendAt: Instant, val occurrence: Instant, val reminder: UUID)
