/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.notification.Schedule
import java.time.Duration
import java.time.Instant

/**
 * When an event's reminders go out: each enabled reminder for each occurrence of its
 * schedule, in order of send time, then occurrence, then the reminder's place in the
 * event. A reminder before an occurrence may go out before an earlier occurrence's
 * reminder after it, so occurrences are looked at until none could come sooner.
 */
object EventCalendar {

    /** How many occurrences to look through before giving up. */
    private const val MAX_OCCURRENCES = 5_000

    /**
     * The first reminder after [cursor], or, without one, the first sent strictly after
     * [after]; null when there is none left.
     */
    fun next(schedule: Schedule, reminders: List<Reminder>, after: Instant, cursor: PendingReminder? = null): PendingReminder? {
        val enabled = reminders.withIndex().filter { it.value.enabled }
        if (enabled.isEmpty()) return null
        val from = cursor?.sendAt ?: after
        // The furthest any reminder goes out after its occurrence, and before it.
        val latest = enabled.filter { it.value.timing == ReminderTiming.AFTER }.maxOfOrNull { it.value.reach() } ?: Duration.ZERO
        val earliest = enabled.filter { it.value.timing == ReminderTiming.BEFORE }.maxOfOrNull { it.value.reach() } ?: Duration.ZERO
        val cursorPosition = cursor?.let { c -> reminders.indexOfFirst { it.id == c.reminder } } ?: -1

        var best: Candidate? = null
        // An occurrence just before [from] can still have a reminder after it to come.
        var occurrence = firstOccurrenceFrom(schedule, from.minus(latest).minusSeconds(1)) ?: return null
        repeat(MAX_OCCURRENCES) {
            // No later occurrence can have a reminder before the best found.
            if (best != null && occurrence.minus(earliest) > best!!.sendAt) return best?.pending
            for ((position, reminder) in enabled) {
                val candidate = Candidate(reminder.sendAt(occurrence, schedule.zone), occurrence, position, reminder.id)
                val laterThanCursor = if (cursor == null) candidate.sendAt > after else candidate.isAfter(cursor.sendAt, cursor.occurrence, cursorPosition)
                if (laterThanCursor && (best == null || candidate.isBefore(best!!))) best = candidate
            }
            occurrence = schedule.nextAfter(occurrence) ?: return best?.pending
        }
        return best?.pending
    }

    /** Every reminder for the occurrences from [after] on, in order, up to [count]. */
    fun upcoming(schedule: Schedule, reminders: List<Reminder>, after: Instant, count: Int): List<PendingReminder> =
        generateSequence(next(schedule, reminders, after)) { next(schedule, reminders, after, it) }.take(count).toList()

    /** The next [count] occurrences after [after]. */
    fun occurrences(schedule: Schedule, after: Instant, count: Int): List<Instant> = schedule.upcoming(after, count)

    /** The schedule's first occurrence at or after [from]. */
    private fun firstOccurrenceFrom(schedule: Schedule, from: Instant): Instant? = schedule.nextAfter(from.minusNanos(1))

    private data class Candidate(val sendAt: Instant, val occurrence: Instant, val position: Int, val reminder: java.util.UUID) {
        val pending get() = PendingReminder(sendAt, occurrence, reminder)

        fun isBefore(other: Candidate) = compareValuesBy(this, other, { it.sendAt }, { it.occurrence }, { it.position }) < 0

        fun isAfter(sendAt: Instant, occurrence: Instant, position: Int) =
            compareValuesBy(this, Candidate(sendAt, occurrence, position, reminder), { it.sendAt }, { it.occurrence }, { it.position }) > 0
    }
}
