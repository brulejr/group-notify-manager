/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A recurring schedule written in words, e.g. "every other month on the 3rd Tuesday at
 * 6pm", read by [PatternParser]. Unlike cron, an interval is counted from [start], so
 * "every other week" means every other week from that day.
 *
 * Every run is at [time] on a date that is a whole number of [interval]s of [frequency]
 * from [start]:
 * - [Frequency.HOUR]: unlike the others, every [interval] hours from [time] on [start],
 *   on the local clock, so `every 2 hours at 8am` stays on 8, 10, 12…. On the night the
 *   clocks go forward, a run in the missing hour is sent at the hour after it, once; when
 *   they go back, the repeated hour is not sent twice.
 * - [Frequency.DAY]: that date.
 * - [Frequency.WEEK]: each of [weekdays] in that week (weeks run Monday to Sunday), or
 *   [start]'s weekday when none are given.
 * - [Frequency.MONTH]: the [ordinal] [ordinalDay] of that month (1 to 5, or -1 for the
 *   last), or day [dayOfMonth], or [start]'s day of the month when neither is given. A
 *   month without that day, a fifth Tuesday or a 31st, is skipped.
 *
 * Times are local, in the notification's zone: across a daylight saving change it stays
 * at the same local time, and a time that does not exist that night moves forward.
 *
 * @property until the last day it may run on, inclusive.
 */
data class SchedulePattern(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val ordinal: Int? = null,
    val ordinalDay: DayOfWeek? = null,
    val dayOfMonth: Int? = null,
    val time: LocalTime,
    val start: LocalDate,
    val until: LocalDate? = null,
) {
    init {
        require(interval in 1..MAX_INTERVAL) { "the interval must be between 1 and $MAX_INTERVAL" }
        require(frequency == Frequency.WEEK || weekdays.isEmpty()) { "weekdays belong to a weekly pattern" }
        require(frequency == Frequency.MONTH || (ordinal == null && dayOfMonth == null)) { "a day of the month belongs to a monthly pattern" }
        require((ordinal == null) == (ordinalDay == null)) { "an ordinal needs its weekday" }
        require(ordinal == null || ordinal == LAST || ordinal in 1..5) { "the ordinal must be 1 to 5, or last" }
        require(ordinal == null || dayOfMonth == null) { "a month runs on a weekday or a day number, not both" }
        require(dayOfMonth == null || dayOfMonth in 1..31) { "the day of the month must be 1 to 31" }
        require(until == null || !until.isBefore(start)) { "it must end on or after it starts" }
    }

    /** The first run strictly after [after], in [after]'s zone, or null if there is none. */
    fun next(after: ZonedDateTime): ZonedDateTime? {
        if (frequency == Frequency.HOUR) return nextHour(after)
        val zone = after.zone
        val from = maxOf(after.toLocalDate(), start)
        var period = firstPeriod(from)
        repeat(MAX_PERIODS) {
            for (date in datesIn(period)) {
                if (date.isBefore(start)) continue
                if (until != null && date.isAfter(until)) return null
                val run = ZonedDateTime.of(date, time, zone)
                if (run.isAfter(after)) return run
            }
            period++
        }
        return null
    }

    /** An hourly pattern's next run: whole numbers of [interval] hours on from [time] on [start], on the local clock. */
    private fun nextHour(after: ZonedDateTime): ZonedDateTime? {
        val anchor = start.atTime(time)
        val local = after.toLocalDateTime()
        // From the step at or just before [after]; a daylight saving change moves it by an hour at most.
        var step = maxOf(0L, ChronoUnit.HOURS.between(anchor, local) / interval - 1)
        repeat(MAX_PERIODS) {
            val at = anchor.plusHours(step * interval)
            if (until != null && at.toLocalDate().isAfter(until)) return null
            val run = ZonedDateTime.of(at, after.zone)
            if (run.isAfter(after)) return run
            step++
        }
        return null
    }

    /** The pattern in words, for people: `Every other month on the 3rd Tuesday at 6:00 PM, from Monday, February 1, 2027`. */
    fun describe(): String = buildString {
        append("Every ")
        append(
            when (interval) {
                1 -> frequency.singular
                2 -> "other ${frequency.singular}"
                else -> "$interval ${frequency.plural}"
            },
        )
        when {
            weekdays.isNotEmpty() -> append(" on ").append(list(weekdays.sorted().map(::name)))
            ordinal != null -> append(" on the ").append(ordinalWord(ordinal)).append(' ').append(name(checkNotNull(ordinalDay)))
            dayOfMonth != null -> append(" on day ").append(dayOfMonth).append(if (dayOfMonth > 28) " (skipped in months without one)" else "")
        }
        if (frequency == Frequency.HOUR) {
            append(" from ").append(time.format(TIME_WORDS)).append(" on ").append(start.format(DATE_WORDS))
        } else {
            append(" at ").append(time.format(TIME_WORDS))
            append(", from ").append(start.format(DATE_WORDS))
        }
        until?.let { append(", until ").append(it.format(DATE_WORDS)) }
    }

    /**
     * The pattern in the parser's own words, with its start always given, so it reads back
     * to the same pattern whenever it is read: what is stored.
     */
    fun canonical(): String = buildString {
        append("every ")
        if (interval == 1) append(frequency.singular) else append(interval).append(' ').append(frequency.plural)
        when {
            weekdays.isNotEmpty() -> append(" on ").append(weekdays.sorted().joinToString(", ") { it.name.lowercase() })
            ordinal != null -> append(" on the ").append(ordinalWord(ordinal)).append(' ').append(checkNotNull(ordinalDay).name.lowercase())
            dayOfMonth != null -> append(" on day ").append(dayOfMonth)
        }
        append(" at ").append(time.format(TIME_CANONICAL))
        append(" starting ").append(start)
        until?.let { append(" until ").append(it) }
    }

    // ---- periods ----

    /** The period, counted from [start]'s, that holds [date] or the first one after it. */
    private fun firstPeriod(date: LocalDate): Long = when (frequency) {
        Frequency.HOUR -> error("an hourly pattern is not counted in days; see nextHour")
        Frequency.DAY -> ChronoUnit.DAYS.between(start, date) / interval
        Frequency.WEEK -> ChronoUnit.WEEKS.between(monday(start), monday(date)) / interval
        Frequency.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date)) / interval
    }

    /** The dates the pattern runs on in period [n], in order. */
    private fun datesIn(n: Long): List<LocalDate> = when (frequency) {
        Frequency.HOUR -> error("an hourly pattern is not counted in days; see nextHour")
        Frequency.DAY -> listOf(start.plusDays(n * interval))
        Frequency.WEEK -> {
            val week = monday(start).plusWeeks(n * interval)
            weekdays.ifEmpty { setOf(start.dayOfWeek) }.sorted().map { week.plusDays(it.value - 1L) }
        }
        Frequency.MONTH -> listOfNotNull(dayIn(YearMonth.from(start).plusMonths(n * interval)))
    }

    /** The day the pattern runs on in [month], or null if that month has none. */
    private fun dayIn(month: YearMonth): LocalDate? {
        if (ordinal != null) {
            val day = checkNotNull(ordinalDay)
            val date = if (ordinal == LAST) {
                month.atEndOfMonth().with(TemporalAdjusters.lastInMonth(day))
            } else {
                month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(ordinal, day))
            }
            // A fifth Tuesday runs on into the next month; that month has none.
            return date.takeIf { YearMonth.from(it) == month }
        }
        val day = dayOfMonth ?: start.dayOfMonth
        return if (day <= month.lengthOfMonth()) month.atDay(day) else null
    }

    private fun monday(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    companion object {
        /** [ordinal] for "the last". */
        const val LAST = -1

        const val MAX_INTERVAL = 99

        /** How many periods to look through before giving up, e.g. for a 5th weekday every year: years of runs. */
        private const val MAX_PERIODS = 2_000

        private val TIME_WORDS = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private val TIME_CANONICAL = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_WORDS = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)

        fun ordinalWord(ordinal: Int) = when (ordinal) {
            LAST -> "last"
            1 -> "1st"
            2 -> "2nd"
            3 -> "3rd"
            else -> "${ordinal}th"
        }

        private fun name(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.US)

        private fun list(items: List<String>) = when (items.size) {
            1 -> items.single()
            else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
        }
    }
}
