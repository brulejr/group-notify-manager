/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Checks a schedule as written, a one-off, cron or pattern in a zone, and builds it:
 * shared by notifications and events. Each problem is reported against its input.
 */
object ScheduleRules {

    /**
     * The schedule [input] describes, or null with its problems reported. A one-off must
     * be in the future, unless it is [sentRunAt], the time it was already sent at, so a
     * sent one can still be edited without choosing a new time.
     */
    fun build(input: ScheduleInput, defaultZone: String, now: Instant, sentRunAt: Instant?, problem: (String, String) -> Boolean): Schedule? {
        val zone = input.zone.trim().ifEmpty { defaultZone }.let { runCatching { ZoneId.of(it) }.getOrNull() }
        if (zone == null) {
            problem("zone", "is not a time zone")
            return null
        }
        return schedule(input, zone, sentRunAt, now, problem)
    }

    private fun schedule(
        draft: ScheduleInput,
        zone: ZoneId,
        sentRunAt: Instant?,
        now: Instant,
        problem: (String, String) -> Boolean,
    ): Schedule? = when (draft.kind) {
        ScheduleKind.ONCE -> {
            val runAt = draft.runAt?.atZone(zone)?.toInstant()
            when {
                runAt == null -> null.also { problem("runAt", "is required") }
                runAt <= now && runAt != sentRunAt ->
                    null.also { problem("runAt", "must be in the future") }
                else -> Schedule(ScheduleKind.ONCE, zone, runAt = runAt)
            }
        }
        ScheduleKind.RECURRING -> {
            val cron = draft.cron.trim()
            val cronProblem = if (cron.isEmpty()) "is required" else CronSpec.problem(cron)
            if (cronProblem != null) {
                null.also { problem("cron", cronProblem) }
            } else {
                val until = draft.until?.atTime(LocalTime.MAX)?.atZone(zone)?.toInstant()
                val schedule = Schedule(ScheduleKind.RECURRING, zone, cron = cron, until = until)
                if (until != null && until <= now) {
                    null.also { problem("until", "must not have passed") }
                } else if (schedule.nextAfter(now) == null) {
                    null.also { problem("cron", "never runs before the end date") }
                } else {
                    schedule
                }
            }
        }
        // Stored as its canonical words, start included, so "every other" keeps counting
        // from the same day however often it is edited.
        ScheduleKind.PATTERN -> {
            val text = draft.pattern.trim()
            val pattern = if (text.isEmpty()) {
                null.also { problem("pattern", "Write a pattern, e.g. “every week on tuesday at 6pm”.") }
            } else {
                runCatching { PatternParser.parse(text, now.atZone(zone).toLocalDate()) }
                    .onFailure { problem("pattern", it.message ?: "is not understood") }
                    .getOrNull()
            }
            pattern?.let {
                val schedule = Schedule(
                    ScheduleKind.PATTERN, zone, pattern = it.canonical(),
                    until = it.until?.atTime(LocalTime.MAX)?.atZone(zone)?.toInstant(),
                )
                when {
                    it.until != null && schedule.until!! <= now -> null.also { problem("pattern", "Its end date has passed.") }
                    schedule.nextAfter(now) == null -> null.also { problem("pattern", "It never runs before its end date.") }
                    else -> schedule
                }
            }
        }
    }
}
