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
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScheduleTest {

    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `five-field cron is read with seconds at zero, six-field and macros as Spring reads them`() {
        assertEquals("0 0 18 * * TUE", CronSpec.normalize("0 18 * * TUE"))
        assertEquals("0 0 18 * * TUE", CronSpec.normalize("  0  0 18 * * TUE "))
        assertEquals("@weekly", CronSpec.normalize("@WEEKLY"))
        assertFailsWith<IllegalArgumentException> { CronSpec.normalize("0 18 *") }
        assertNull(CronSpec.problem("0 19 * * TUE#2"))
        assertNotNull(CronSpec.problem("0 25 * * *"))
    }

    @Test
    fun `a recurring schedule keeps local time across the end of daylight saving`() {
        val tuesdays = Schedule(ScheduleKind.RECURRING, newYork, cron = "0 18 * * TUE")

        val runs = tuesdays.upcoming(Instant.parse("2026-10-26T00:00:00Z"), 2)

        // 27 Oct is still EDT (UTC-4); 3 Nov is EST (UTC-5). Both are 18:00 local.
        assertEquals(listOf(Instant.parse("2026-10-27T22:00:00Z"), Instant.parse("2026-11-03T23:00:00Z")), runs)
    }

    @Test
    fun `nothing is due after the end date`() {
        val until = Instant.parse("2026-11-04T04:59:59Z")
        val schedule = Schedule(ScheduleKind.RECURRING, newYork, cron = "0 18 * * TUE", until = until)

        assertEquals(1, schedule.upcoming(Instant.parse("2026-10-30T00:00:00Z"), 5).size)
        assertNull(schedule.nextAfter(Instant.parse("2026-11-04T00:00:00Z")))
    }

    @Test
    fun `a one-off is due once`() {
        val at = Instant.parse("2026-12-01T23:00:00Z")
        val once = Schedule(ScheduleKind.ONCE, newYork, runAt = at)

        assertEquals(at, once.nextAfter(at.minusSeconds(1)))
        assertNull(once.nextAfter(at))
    }

    @Test
    fun `the second Tuesday and the last day of the month`() {
        val from = Instant.parse("2026-11-01T00:00:00Z")

        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), Schedule(ScheduleKind.RECURRING, newYork, cron = "0 19 * * TUE#2").nextAfter(from))
        assertEquals(Instant.parse("2026-11-30T22:00:00Z"), Schedule(ScheduleKind.RECURRING, newYork, cron = "0 17 L * *").nextAfter(from))
    }
}
