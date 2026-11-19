/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.notification.PatternParser
import io.brulejr.groupnotify.notification.Schedule
import io.brulejr.groupnotify.notification.ScheduleKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventCalendarTest {

    private val zone = ZoneId.of("America/New_York")

    private fun pattern(text: String) =
        Schedule(ScheduleKind.PATTERN, zone, pattern = PatternParser.parse(text, LocalDate.of(2026, 11, 10)).canonical())

    private fun reminder(amount: Int, unit: ReminderUnit, timing: ReminderTiming, enabled: Boolean = true) =
        Reminder(UUID.randomUUID(), amount, unit, timing, enabled = enabled)

    private fun local(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun sends(schedule: Schedule, reminders: List<Reminder>, after: Instant, count: Int) =
        EventCalendar.upcoming(schedule, reminders, after, count).map { p ->
            p.sendAt.atZone(zone).toLocalDateTime().toString() + " " + reminders.first { it.id == p.reminder }.describe()
        }

    @Test
    fun `reminders go out before, at and after each occurrence, in time order across occurrences`() {
        val weekly = pattern("every tuesday at 7pm starting 2026-11-10")
        val reminders = listOf(
            reminder(1, ReminderUnit.DAYS, ReminderTiming.BEFORE),
            reminder(1, ReminderUnit.HOURS, ReminderTiming.BEFORE),
            reminder(0, ReminderUnit.MINUTES, ReminderTiming.AT_START),
            reminder(1, ReminderUnit.DAYS, ReminderTiming.AFTER),
        )

        assertEquals(
            listOf(
                "2026-11-10T18:00 1 hour before",
                "2026-11-10T19:00 at the start",
                "2026-11-11T19:00 1 day after",
                "2026-11-16T19:00 1 day before",
                "2026-11-17T18:00 1 hour before",
                "2026-11-17T19:00 at the start",
            ),
            sends(weekly, reminders, local("2026-11-10T07:00"), 6),
        )
    }

    @Test
    fun `two reminders at the same instant are both sent, the earlier occurrence's first`() {
        val weekly = pattern("every tuesday at 7pm starting 2026-11-10")
        val weekBefore = reminder(1, ReminderUnit.WEEKS, ReminderTiming.BEFORE)
        val atStart = reminder(0, ReminderUnit.MINUTES, ReminderTiming.AT_START)
        val reminders = listOf(weekBefore, atStart)

        val first = EventCalendar.next(weekly, reminders, local("2026-11-17T12:00"))!!
        assertEquals(PendingReminder(local("2026-11-17T19:00"), local("2026-11-17T19:00"), atStart.id), first)
        val second = EventCalendar.next(weekly, reminders, first.sendAt, cursor = first)!!
        assertEquals(PendingReminder(local("2026-11-17T19:00"), local("2026-11-24T19:00"), weekBefore.id), second)
        val third = EventCalendar.next(weekly, reminders, second.sendAt, cursor = second)!!
        assertEquals(PendingReminder(local("2026-11-24T19:00"), local("2026-11-24T19:00"), atStart.id), third)
    }

    @Test
    fun `days are counted on the local calendar across daylight saving`() {
        // Monday 2 November 2026, the day after the clocks went back.
        val once = Schedule(ScheduleKind.ONCE, zone, runAt = local("2026-11-02T19:00"))
        val twoDays = reminder(2, ReminderUnit.DAYS, ReminderTiming.BEFORE)

        val send = EventCalendar.next(once, listOf(twoDays), local("2026-10-20T00:00"))!!.sendAt
        assertEquals(LocalDateTime.parse("2026-10-31T19:00"), send.atZone(zone).toLocalDateTime())
        assertEquals(Duration.ofHours(49), Duration.between(send, local("2026-11-02T19:00")))
    }

    @Test
    fun `a one-off past its start still sends its reminders after, then none`() {
        val once = Schedule(ScheduleKind.ONCE, zone, runAt = local("2026-11-10T19:00"))
        val reminders = listOf(reminder(1, ReminderUnit.HOURS, ReminderTiming.BEFORE), reminder(2, ReminderUnit.DAYS, ReminderTiming.AFTER))

        assertEquals(listOf("2026-11-12T19:00 2 days after"), sends(once, reminders, local("2026-11-10T20:00"), 5))
        assertNull(EventCalendar.next(once, reminders, local("2026-11-13T00:00")))
    }

    @Test
    fun `disabled reminders are not sent, and with none enabled nothing is`() {
        val weekly = pattern("every tuesday at 7pm starting 2026-11-10")
        val off = reminder(1, ReminderUnit.HOURS, ReminderTiming.BEFORE, enabled = false)

        assertNull(EventCalendar.next(weekly, listOf(off), local("2026-11-10T07:00")))
        assertEquals(
            listOf("2026-11-10T19:00 at the start"),
            sends(weekly, listOf(off, reminder(0, ReminderUnit.MINUTES, ReminderTiming.AT_START)), local("2026-11-10T07:00"), 1),
        )
    }

    @Test
    fun `reminders say how far off the occurrence is`() {
        assertEquals("in 2 hours", reminder(2, ReminderUnit.HOURS, ReminderTiming.BEFORE).relative())
        assertEquals("now", reminder(0, ReminderUnit.MINUTES, ReminderTiming.AT_START).relative())
        assertEquals("1 day ago", reminder(1, ReminderUnit.DAYS, ReminderTiming.AFTER).relative())
        assertEquals("1 week before", reminder(1, ReminderUnit.WEEKS, ReminderTiming.BEFORE).describe())
    }
}
