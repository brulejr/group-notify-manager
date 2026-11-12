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
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Reads a [SchedulePattern] from words. Clauses may come in any order, each at most once,
 * with commas or "and" between them as the author likes; case does not matter:
 *
 * - `every [other | N] (hour | day | week | month)`, or `every [other | N] <weekday>` for a
 *   weekly pattern on that day: required.
 * - `on the (1st … 5th | first … fifth | last) <weekday>`: monthly.
 * - `on <weekday>[, <weekday> … and <weekday>]`: weekly.
 * - `on day <n>`, or `on the <n>th`: monthly.
 * - `at <time>`: `18:00`, `6pm`, `6:30 pm`, `noon`, `midnight`. Required, except for an
 *   hourly pattern, where it is the time it counts from: midnight unless said.
 * - `starting <date>`: `2027-02-01`, `February 1, 2027`, `1 Feb 2027`, or without a year
 *   the next such day; `today`, `tomorrow`. Default today. Also `from`.
 * - `until <date>`: the last day, inclusive. Also `through`.
 *
 * Deterministic: the same words always give the same pattern, and words it does not know
 * are named in the error rather than guessed at. Throws [IllegalArgumentException] with a
 * message fit to show the author.
 */
object PatternParser {

    /** [text] read as a pattern; a start or end without a year, or `today`, is taken from [today]. */
    fun parse(text: String, today: LocalDate): SchedulePattern = Reader(tokens(text), today).read()

    /** Why [text] is not a usable pattern, or null if it is. */
    fun problem(text: String, today: LocalDate): String? = runCatching { parse(text, today) }.exceptionOrNull()?.message

    /** Ready-made patterns the console offers, with what each means. */
    val EXAMPLES = linkedMapOf(
        "every week on tuesday at 6pm" to "every Tuesday at 6:00 PM",
        "every other week on monday, thursday at 7:30pm" to "Mondays and Thursdays, every other week",
        "every month on the 2nd tuesday at 19:00" to "the second Tuesday of each month",
        "every other month on the 3rd tuesday at 6pm" to "the third Tuesday of every other month",
        "every month on the last friday at noon" to "the last Friday of each month",
        "every month on day 15 at 9am" to "the 15th of each month",
        "every 3 days at 8am" to "every third day",
        "every 2 hours at 8am" to "every two hours, from 8 in the morning",
    )

    private val KEYWORDS = setOf("every", "on", "at", "starting", "from", "until", "through", "till")

    private val WEEKDAYS: Map<String, DayOfWeek> = DayOfWeek.entries.flatMap { day ->
        val full = day.name.lowercase()
        listOf(full to day, "${full}s" to day, full.take(3) to day) + when (day) {
            DayOfWeek.TUESDAY -> listOf("tues" to day)
            DayOfWeek.WEDNESDAY -> listOf("weds" to day)
            DayOfWeek.THURSDAY -> listOf("thur" to day, "thurs" to day)
            else -> emptyList()
        }
    }.toMap()

    private val ORDINALS = mapOf(
        "1st" to 1, "first" to 1, "2nd" to 2, "second" to 2, "3rd" to 3, "third" to 3,
        "4th" to 4, "fourth" to 4, "5th" to 5, "fifth" to 5, "last" to SchedulePattern.LAST,
    )

    private val UNITS = mapOf(
        "hour" to Frequency.HOUR, "hours" to Frequency.HOUR, "hr" to Frequency.HOUR, "hrs" to Frequency.HOUR,
        "day" to Frequency.DAY, "days" to Frequency.DAY,
        "week" to Frequency.WEEK, "weeks" to Frequency.WEEK,
        "month" to Frequency.MONTH, "months" to Frequency.MONTH,
    )

    private val DAY_NUMBER = Regex("(\\d{1,2})(st|nd|rd|th)?")
    private val CLOCK = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm)?")

    private val DATES_WITH_YEAR = listOf("MMMM d yyyy", "MMM d yyyy", "d MMMM yyyy", "d MMM yyyy").map(::formatter)
    private val DATES_WITHOUT_YEAR = listOf("MMMM d", "MMM d", "d MMMM", "d MMM").map(::formatter)

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.US)

    /** Lower-case words; commas and "and" separate, so they are kept as their own token. */
    private fun tokens(text: String): List<String> = text.lowercase(Locale.US)
        .replace(",", " , ")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }

    private class Reader(private val words: List<String>, private val today: LocalDate) {
        private var i = 0
        private val seen = mutableSetOf<String>()

        private var frequency: Frequency? = null
        private var interval = 1
        private val weekdays = mutableSetOf<DayOfWeek>()
        private var ordinal: Int? = null
        private var ordinalDay: DayOfWeek? = null
        private var dayOfMonth: Int? = null
        private var time: LocalTime? = null
        private var start: LocalDate? = null
        private var until: LocalDate? = null

        fun read(): SchedulePattern {
            if (words.isEmpty()) fail("Write a pattern, e.g. “every week on tuesday at 6pm”.")
            while (i < words.size) {
                when (val word = words[i++]) {
                    ",", "and" -> Unit
                    "every" -> once("every") { every() }
                    "on" -> on()
                    "at" -> once("at") { time = time() }
                    "starting", "from" -> once("starting") { start = date(word) }
                    "until", "through", "till" -> once("until") { until = date(word) }
                    else -> fail("“$word” is not understood here. Start a part with every, on, at, starting or until.")
                }
            }
            return build()
        }

        private fun every() {
            when (val word = next("a unit after “every”, e.g. “every week”")) {
                "other" -> interval = 2
                else -> word.toIntOrNull()?.let { n ->
                    if (n !in 1..SchedulePattern.MAX_INTERVAL) fail("“every $n” must be between 1 and ${SchedulePattern.MAX_INTERVAL}.")
                    interval = n
                } ?: run { i-- }
            }
            val word = next("hour, day, week or month after “every”")
            val unit = UNITS[word]
            val weekday = WEEKDAYS[word]
            when {
                unit != null -> frequency = unit
                // "every tuesday", "every other thursday": weekly, on that day.
                weekday != null -> {
                    frequency = Frequency.WEEK
                    weekdays += weekday
                }
                else -> fail("“every $word” is not understood: say hour, day, week, month or a weekday.")
            }
        }

        private fun on() {
            val word = next("what follows “on”: a weekday, “the 3rd tuesday” or “day 15”")
            when {
                word == "the" -> once("on the") { onThe() }
                word == "day" -> once("on day") { dayOfMonth = dayNumber(next("a day number after “on day”")) }
                WEEKDAYS.containsKey(word) -> {
                    i--
                    weekdayList()
                }
                else -> fail("“on $word” is not understood: say a weekday, “the 3rd tuesday” or “day 15”.")
            }
        }

        private fun onThe() {
            val word = next("an ordinal after “on the”, e.g. “on the 3rd tuesday”")
            val ord = ORDINALS[word]
            val weekday = words.getOrNull(i)?.let(WEEKDAYS::get)
            when {
                ord != null && weekday != null -> {
                    i++
                    ordinal = ord
                    ordinalDay = weekday
                }
                // "on the 15th": a day of the month.
                DAY_NUMBER.matches(word) -> dayOfMonth = dayNumber(word)
                ord != null -> fail("“on the $word” needs a weekday after it, e.g. “on the $word tuesday”.")
                else -> fail("“on the $word” is not understood: say 1st to 5th or last, then a weekday.")
            }
        }

        private fun weekdayList() {
            while (i < words.size) {
                val word = words[i]
                when {
                    WEEKDAYS.containsKey(word) -> {
                        weekdays += WEEKDAYS.getValue(word)
                        i++
                    }
                    // A separator continues the list only if a weekday follows it.
                    (word == "," || word == "and") && words.getOrNull(i + 1)?.let(WEEKDAYS::containsKey) == true -> i++
                    else -> return
                }
            }
        }

        private fun time(): LocalTime {
            val word = next("a time after “at”, e.g. “at 6pm” or “at 18:00”")
            when (word) {
                "noon", "midday" -> return LocalTime.NOON
                "midnight" -> return LocalTime.MIDNIGHT
            }
            // "6 pm" as well as "6pm".
            val text = if (words.getOrNull(i) == "am" || words.getOrNull(i) == "pm") word + words[i++] else word
            val match = CLOCK.matchEntire(text) ?: fail("“at $text” is not a time: try “at 6pm” or “at 18:00”.")
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
            val half = match.groupValues[3]
            if (minute > 59) fail("“at $text” is not a time: minutes run to 59.")
            val hour24 = when (half) {
                "" -> hour.takeIf { it in 0..23 }
                "am" -> hour.takeIf { it in 1..12 }?.let { it % 12 }
                else -> hour.takeIf { it in 1..12 }?.let { it % 12 + 12 }
            } ?: fail("“at $text” is not a time: hours run to ${if (half.isEmpty()) 23 else 12}.")
            return LocalTime.of(hour24, minute)
        }

        /** A date: every word up to the next part, e.g. `February 1, 2027`. */
        private fun date(keyword: String): LocalDate {
            val parts = mutableListOf<String>()
            while (i < words.size && words[i] !in KEYWORDS) {
                if (words[i] != ",") parts += words[i].replace(DAY_SUFFIX, "$1")
                i++
            }
            if (parts.isEmpty()) fail("Say a date after “$keyword”, e.g. “$keyword 2027-02-01”.")
            val text = parts.joinToString(" ")
            when (text) {
                "today" -> return today
                "tomorrow" -> return today.plusDays(1)
            }
            runCatching { return LocalDate.parse(text) }
            DATES_WITH_YEAR.forEach { f -> runCatching { return LocalDate.parse(text, f) } }
            DATES_WITHOUT_YEAR.forEach { f ->
                try {
                    // Without a year: the next such day, today included.
                    val day = MonthDay.parse(text, f).atYear(today.year)
                    return if (day.isBefore(today)) MonthDay.from(day).atYear(today.year + 1) else day
                } catch (_: DateTimeParseException) {
                    // try the next form
                }
            }
            fail("“$keyword $text” is not a date: try “$keyword 2027-02-01” or “$keyword February 1, 2027”.")
        }

        private fun dayNumber(word: String): Int {
            val n = DAY_NUMBER.matchEntire(word)?.groupValues?.get(1)?.toInt()
            if (n == null || n !in 1..31) fail("“$word” is not a day of the month: say 1 to 31.")
            return n
        }

        private fun build(): SchedulePattern {
            val frequency = frequency ?: fail("Say how often, e.g. “every week”.")
            // An hourly pattern counts from midnight unless told otherwise.
            val time = time ?: if (frequency == Frequency.HOUR) LocalTime.MIDNIGHT else fail("Say what time, e.g. “at 6pm”.")
            when (frequency) {
                Frequency.HOUR, Frequency.DAY -> if (weekdays.isNotEmpty() || ordinal != null || dayOfMonth != null) {
                    fail("“every ${frequency.singular}” runs every ${frequency.singular}: for particular days say “every week on …” or “every month on …”.")
                }
                Frequency.WEEK -> if (ordinal != null || dayOfMonth != null) {
                    val said = ordinal?.let { "on the ${SchedulePattern.ordinalWord(it)} ${checkNotNull(ordinalDay).name.lowercase()}" } ?: "on day $dayOfMonth"
                    fail("“$said” is a day of a month: say “every month”, not “every week”.")
                }
                Frequency.MONTH -> {
                    if (weekdays.isNotEmpty()) fail("Say which one in the month, e.g. “on the 1st ${weekdays.first().name.lowercase()}”.")
                    if (ordinal != null && dayOfMonth != null) fail("Say a weekday or a day number, not both.")
                }
            }
            val start = start ?: today
            if (until?.isBefore(start) == true) fail("It ends before it starts: “until” must be on or after the start.")
            return SchedulePattern(frequency, interval, weekdays.toSet(), ordinal, ordinalDay, dayOfMonth, time, start, until)
        }

        private fun next(what: String): String = words.getOrNull(i++) ?: fail("Say $what.")

        private fun once(part: String, block: () -> Unit) {
            if (!seen.add(part)) fail("“$part” is given twice.")
            block()
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

        private companion object {
            val DAY_SUFFIX = Regex("^(\\d{1,2})(st|nd|rd|th)$")
        }
    }
}
