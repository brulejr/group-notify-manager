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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchedulePatternTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val zone = ZoneId.of("America/New_York")

    private fun parse(text: String) = PatternParser.parse(text, today)

    /** The next [count] runs after [from], as local date-times. */
    private fun runs(text: String, count: Int = 4, from: LocalDateTime = today.atStartOfDay()): List<LocalDateTime> {
        val pattern = parse(text)
        return generateSequence(pattern.next(from.atZone(zone))) { pattern.next(it) }.take(count).map { it.toLocalDateTime() }.toList()
    }

    private fun at(date: String, time: String = "18:00") = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))

    private fun problem(text: String) = assertFailsWith<IllegalArgumentException> { parse(text) }.message.orEmpty()

    // ---- what it means ----

    @Test
    fun `the 3rd tuesday of every other month, counted from its start`() {
        assertEquals(
            listOf(at("2027-02-16"), at("2027-04-20"), at("2027-06-15"), at("2027-08-17")),
            runs("every other month on the 3rd tuesday at 6pm starting 2027-02-01"),
        )
    }

    @Test
    fun `every other week on several days, from a start mid-week`() {
        assertEquals(
            listOf(at("2026-11-12", "19:30"), at("2026-11-23", "19:30"), at("2026-11-26", "19:30"), at("2026-12-07", "19:30")),
            runs("every other week on monday and thursday at 7:30pm starting 2026-11-11"),
        )
    }

    @Test
    fun `every N days, and a weekly pattern on its start's weekday when none is said`() {
        assertEquals(listOf(at("2026-11-10", "08:00"), at("2026-11-13", "08:00"), at("2026-11-16", "08:00")), runs("every 3 days at 8am", 3))
        // 2026-11-10 is a Tuesday.
        assertEquals(listOf(at("2026-11-10", "12:00"), at("2026-11-17", "12:00")), runs("every week at noon", 2))
        assertEquals(listOf(at("2026-11-12", "12:00"), at("2026-11-26", "12:00")), runs("every other thursday at noon", 2))
    }

    @Test
    fun `every N hours, counted from its time on its start day, on the local clock`() {
        assertEquals(
            listOf(at("2026-11-10", "08:00"), at("2026-11-10", "10:00"), at("2026-11-10", "12:00"), at("2026-11-10", "14:00")),
            runs("every 2 hours at 8am"),
        )
        // Not a divisor of 24: 8, 13, 18, 23, then on across midnight rather than starting again each day.
        assertEquals(listOf(at("2026-11-10", "23:00"), at("2026-11-11", "04:00"), at("2026-11-11", "09:00")), runs("every 5 hours at 8am", 3, at("2026-11-10", "19:00")))
        // Without "at", from midnight.
        assertEquals(listOf(at("2026-11-10", "09:00"), at("2026-11-10", "10:00")), runs("every hour", 2, at("2026-11-10", "08:30")))
        assertEquals(listOf(at("2026-11-10", "22:00")), runs("every other hour until 2026-11-10", 5, at("2026-11-10", "21:00")))
    }

    @Test
    fun `hours keep to the clock across daylight saving, sending each run once`() {
        val hourly = parse("every hour starting 2027-03-14")
        // Spring forward: 02:00 does not exist, so that run is at 03:00, and 03:00 is not sent again.
        val spring = generateSequence(hourly.next(ZonedDateTime.of(2027, 3, 14, 0, 30, 0, 0, zone))) { hourly.next(it) }.take(3).toList()
        assertEquals(listOf("01:00", "03:00", "04:00"), spring.map { it.toLocalTime().toString() })

        // Fall back: 01:00 happens twice; it is sent once.
        val autumn = parse("every hour starting 2027-11-07")
        val fall = generateSequence(autumn.next(ZonedDateTime.of(2027, 11, 7, 0, 30, 0, 0, zone))) { autumn.next(it) }.take(3).toList()
        assertEquals(listOf("01:00", "02:00", "03:00"), fall.map { it.toLocalTime().toString() })
    }

    @Test
    fun `a month without the day is skipped, not moved`() {
        assertEquals(listOf(at("2027-03-30", "09:00"), at("2027-06-29", "09:00")), runs("every month on the 5th tuesday at 9am starting 2027-01-01", 2))
        assertEquals(
            listOf(at("2027-01-31", "09:00"), at("2027-03-31", "09:00"), at("2027-05-31", "09:00")),
            runs("every month on day 31 at 9am starting 2027-01-01", 3),
        )
        assertEquals(listOf(at("2027-02-26", "12:00")), runs("every month on the last friday at noon starting 2027-02-01", 1))
    }

    @Test
    fun `it ends on its until day, inclusive`() {
        assertEquals(
            listOf(at("2026-11-17"), at("2026-11-24")),
            runs("every tuesday at 6pm starting 2026-11-11 until 2026-11-24", 5),
        )
    }

    @Test
    fun `local time holds across daylight saving, and a time that does not exist moves forward`() {
        val daily = parse("every day at 18:00 starting 2027-03-13")
        val beforeSpring = daily.next(ZonedDateTime.of(2027, 3, 13, 0, 0, 0, 0, zone))!!
        val afterSpring = daily.next(beforeSpring)!!
        assertEquals(LocalTime.of(18, 0), afterSpring.toLocalTime())
        assertEquals(java.time.Duration.ofHours(23), java.time.Duration.between(beforeSpring, afterSpring))

        // 02:30 on 14 March 2027 never happens in New York.
        val gap = parse("every day at 2:30am starting 2027-03-14").next(ZonedDateTime.of(2027, 3, 13, 12, 0, 0, 0, zone))!!
        assertEquals(LocalTime.of(3, 30), gap.toLocalTime())
    }

    // ---- reading the words ----

    @Test
    fun `each clause is read, in any order, with commas and ands as liked`() {
        val p = parse("At 18:00, starting February 1, 2027, every 2 months, on the third Tuesday")
        assertEquals(SchedulePattern(Frequency.MONTH, 2, ordinal = 3, ordinalDay = DayOfWeek.TUESDAY, time = LocalTime.of(18, 0), start = LocalDate.of(2027, 2, 1)), p)

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), parse("every week on mon, wed and fri at 9").weekdays)
        assertEquals(15, parse("every month on the 15th at 9am").dayOfMonth)
        assertEquals(SchedulePattern.LAST, parse("every month on the last sunday at 9am").ordinal)
        assertEquals(LocalDate.of(2027, 12, 31), parse("every day at 9am until dec 31st 2027").until)
    }

    @Test
    fun `times in the forms people write them`() {
        mapOf(
            "6pm" to LocalTime.of(18, 0), "6 pm" to LocalTime.of(18, 0), "6:30am" to LocalTime.of(6, 30), "18:05" to LocalTime.of(18, 5),
            "noon" to LocalTime.NOON, "midnight" to LocalTime.MIDNIGHT, "12am" to LocalTime.MIDNIGHT, "12pm" to LocalTime.NOON, "7" to LocalTime.of(7, 0),
        ).forEach { (text, time) -> assertEquals(time, parse("every day at $text").time, text) }
    }

    @Test
    fun `a date without a year is the next such day, and today is the default start`() {
        assertEquals(LocalDate.of(2026, 12, 1), parse("every day at 9 starting december 1").start)
        assertEquals(LocalDate.of(2027, 1, 5), parse("every day at 9 starting 5 jan").start)
        assertEquals(today, parse("every day at 9 starting today").start)
        assertEquals(today, parse("every day at 9").start)
    }

    @Test
    fun `its canonical words read back to the same pattern, whatever today is`() {
        listOf(
            "every other month on the 3rd tuesday at 6pm starting 2027-02-01",
            "every 2 weeks on thursday, monday at 7:30pm until 2027-06-30",
            "every month on day 15 at 9am",
            "every month on the last friday at noon",
            "every 3 days at 8am",
            "every 2 hours at 8am starting 2027-02-01",
            "every hour",
        ).forEach { text ->
            val pattern = parse(text)
            assertEquals(pattern, PatternParser.parse(pattern.canonical(), LocalDate.of(1999, 1, 1)), text)
        }
        assertEquals("every 2 months on the 3rd tuesday at 18:00 starting 2027-02-01", parse("every other month on the 3rd tuesday at 6pm starting 2027-02-01").canonical())
    }

    @Test
    fun `it says itself in plain English`() {
        assertEquals(
            "Every other month on the 3rd Tuesday at 6:00 PM, from Monday, February 1, 2027",
            parse("every other month on the 3rd tuesday at 6pm starting 2027-02-01").describe(),
        )
        assertEquals(
            "Every other week on Monday and Thursday at 7:30 PM, from Tuesday, November 10, 2026, until Wednesday, June 30, 2027",
            parse("every 2 weeks on thursday and monday at 7:30pm until 2027-06-30").describe(),
        )
        assertTrue("skipped in months without one" in parse("every month on day 31 at 9am").describe())
        assertEquals("Every other hour from 8:00 AM on Monday, February 1, 2027", parse("every 2 hours at 8am starting 2027-02-01").describe())
        assertEquals("Every 3 hours from 12:00 AM on Tuesday, November 10, 2026", parse("every 3 hours").describe())
        assertEquals("every 2 hours at 08:00 starting 2027-02-01", parse("every 2 hrs at 8am starting 2027-02-01").canonical())
    }

    @Test
    fun `what it cannot read is named, not guessed at`() {
        assertEquals("“fortnight” is not understood here. Start a part with every, on, at, starting or until.", problem("every week at 6pm fortnight"))
        assertEquals("“every fortnight” is not understood: say hour, day, week, month or a weekday.", problem("every fortnight at 6pm"))
        assertEquals("“every hour” runs every hour: for particular days say “every week on …” or “every month on …”.", problem("every hour on tuesday"))
        assertEquals("Say what time, e.g. “at 6pm”.", problem("every week on tuesday"))
        assertEquals("Say how often, e.g. “every week”.", problem("on tuesday at 6pm"))
        assertTrue("every day" in problem("every day on tuesday at 6pm"))
        assertEquals("“on the 3rd tuesday” is a day of a month: say “every month”, not “every week”.", problem("every week on the 3rd tuesday at 6pm"))
        assertEquals("“on day 15” is a day of a month: say “every month”, not “every week”.", problem("every week on day 15 at 6pm"))
        assertTrue("on the 1st tuesday" in problem("every month on tuesday at 6pm"))
        assertEquals("“at” is given twice.", problem("every day at 6pm at 7pm"))
        assertTrue("hours run to 12" in problem("every day at 13pm"))
        assertTrue("not a date" in problem("every day at 9 starting someday"))
        assertTrue("ends before it starts" in problem("every day at 9 starting 2027-02-01 until 2027-01-01"))
        assertTrue("1 to 31" in problem("every month on day 32 at 9"))
        assertTrue("between 1 and 99" in problem("every 100 days at 9"))
        assertNull(PatternParser.problem("every day at 9", today))
    }
}
