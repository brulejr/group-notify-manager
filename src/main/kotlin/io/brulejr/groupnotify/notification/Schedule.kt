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
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a notification is sent.
 *
 * A cron expression is read in [zone], so `0 18 * * TUE` stays at six in the evening
 * local time across a daylight saving change.
 *
 * A pattern, likewise, keeps its local time across a change.
 *
 * @property runAt the one time a [ScheduleKind.ONCE] is sent.
 * @property cron a [ScheduleKind.RECURRING] schedule's expression, as written.
 * @property pattern a [ScheduleKind.PATTERN] schedule's [SchedulePattern.canonical]
 *   words, which always give its start, so it reads back the same whenever it is read.
 * @property until a recurring schedule sends nothing after this; for a pattern, the end
 *   of its last day.
 */
data class Schedule(
    val kind: ScheduleKind,
    val zone: ZoneId,
    val runAt: Instant? = null,
    val cron: String? = null,
    val until: Instant? = null,
    val pattern: String? = null,
) {
    init {
        when (kind) {
            ScheduleKind.ONCE -> require(runAt != null) { "a one-off schedule needs a time" }
            ScheduleKind.RECURRING -> require(cron != null) { "a recurring schedule needs a cron expression" }
            ScheduleKind.PATTERN -> require(pattern != null) { "a pattern schedule needs its pattern" }
        }
    }

    /** The pattern, read; null unless this is a [ScheduleKind.PATTERN]. Its words hold its start, so no today is needed. */
    val parsedPattern: SchedulePattern? get() = pattern?.let { PatternParser.parse(it, LocalDate.EPOCH) }

    /** The first time it is due strictly after [after], or null if there is none. */
    fun nextAfter(after: Instant): Instant? = when (kind) {
        ScheduleKind.ONCE -> runAt?.takeIf { it > after }
        ScheduleKind.RECURRING -> CronSpec.parse(checkNotNull(cron)).next(after.atZone(zone))?.toInstant()
            ?.takeIf { until == null || it <= until }
        ScheduleKind.PATTERN -> checkNotNull(parsedPattern).next(after.atZone(zone))?.toInstant()
    }

    /** The next [count] times it is due after [after]. */
    fun upcoming(after: Instant, count: Int): List<Instant> = generateSequence(nextAfter(after)) { nextAfter(it) }.take(count).toList()
}
