/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.notification.NotificationDelivery
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * A message an event sends relative to each occurrence, e.g. 7 days before, 2 hours
 * before, at the start, or 1 day after. It sends the event's own subject and body unless
 * it gives its own, which are always Markdown.
 *
 * Minutes and hours are elapsed time; days and weeks are counted on the local calendar,
 * so "1 day before" a 6 PM meeting is 6 PM the day before, across a daylight saving
 * change too.
 *
 * @property amount ignored [ReminderTiming.AT_START].
 * @property subject its own subject; blank for the event's.
 * @property body its own body, in Markdown; blank for the event's.
 * @property deliveries where it goes: instances of the event's group, each with its
 *   overrides, chosen for this reminder alone, e.g. the week-before one to the groups.io
 *   list and the hour-before one to Discord.
 */
data class Reminder(
    val id: UUID,
    val amount: Int,
    val unit: ReminderUnit,
    val timing: ReminderTiming,
    val subject: String = "",
    val body: String = "",
    val enabled: Boolean = true,
    val deliveries: List<NotificationDelivery> = emptyList(),
) {
    /** When it goes out for the occurrence starting at [occurrence]. */
    fun sendAt(occurrence: Instant, zone: ZoneId): Instant {
        if (timing == ReminderTiming.AT_START) return occurrence
        val n = (if (timing == ReminderTiming.BEFORE) -amount else amount).toLong()
        return when (unit) {
            ReminderUnit.MINUTES -> occurrence.plusSeconds(n * 60)
            ReminderUnit.HOURS -> occurrence.plusSeconds(n * 3600)
            ReminderUnit.DAYS -> occurrence.atZone(zone).plusDays(n).toInstant()
            ReminderUnit.WEEKS -> occurrence.atZone(zone).plusWeeks(n).toInstant()
        }
    }

    /** "7 days before", "at the start", "1 hour after". */
    fun describe(): String = if (timing == ReminderTiming.AT_START) "at the start" else "${unit.words(amount)} ${timing.label}"

    /** The event as seen from this reminder, for `{{event.when}}`: "in 2 hours", "now", "1 day ago". */
    fun relative(): String = when (timing) {
        ReminderTiming.BEFORE -> "in ${unit.words(amount)}"
        ReminderTiming.AT_START -> "now"
        ReminderTiming.AFTER -> "${unit.words(amount)} ago"
    }

    /** At most how far its send time lies from an occurrence, a day more for the local calendar's sake. */
    fun reach(): java.time.Duration = if (timing == ReminderTiming.AT_START) java.time.Duration.ZERO else when (unit) {
        ReminderUnit.MINUTES -> java.time.Duration.ofMinutes(amount.toLong())
        ReminderUnit.HOURS -> java.time.Duration.ofHours(amount.toLong())
        ReminderUnit.DAYS -> java.time.Duration.ofDays(amount + 1L)
        ReminderUnit.WEEKS -> java.time.Duration.ofDays(amount * 7L + 1)
    }

    companion object {
        /** The largest offset a reminder may have, in each unit: about three months. */
        val MAX = mapOf(ReminderUnit.MINUTES to 10_000, ReminderUnit.HOURS to 2_000, ReminderUnit.DAYS to 90, ReminderUnit.WEEKS to 13)
    }
}
