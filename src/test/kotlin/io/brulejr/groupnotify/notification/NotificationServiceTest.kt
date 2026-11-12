/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.support.Fixtures
import io.brulejr.groupnotify.support.TestUsers.admin
import io.brulejr.groupnotify.support.TestUsers.alice
import io.brulejr.groupnotify.support.TestUsers.bob
import io.brulejr.groupnotify.support.TestUsers.reader
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationServiceTest {

    private val f = Fixtures()
    private val service = f.notifications

    private fun created(draft: NotificationDraft = f.draft()) = assertIs<NotificationResult.Saved>(service.create(draft, alice)).notification

    private fun problems(result: NotificationResult) = assertIs<NotificationResult.Invalid>(result).problems.associate { it.field to it.reason }

    @Test
    fun `a one-off is due at its local time and the creation is recorded`() {
        val n = created()

        assertEquals(NotificationStatus.ACTIVE, n.status)
        assertEquals(Instant.parse("2026-11-11T23:00:00Z"), n.nextRunAt)
        assertEquals("alice", n.createdBy)
        val events = f.audit.history(alice, AuditQuery(notificationId = n.id))
        assertEquals(listOf(AuditEventType.CREATED), events.map { it.type })
    }

    @Test
    fun `every problem is reported at once against its field`() {
        val result = service.create(
            f.draft(runAt = LocalDateTime.of(2026, 1, 1, 9, 0)).copy(title = " ", subject = "", body = "", deliveries = emptyList()),
            alice,
        )

        assertEquals(setOf("title", "subject", "body", "runAt", "deliveries"), problems(result).keys)
    }

    @Test
    fun `a delivery must name one of the group's own instances, once`() {
        val family = f.instance(group = "notify_family", name = "Family channel")
        val result = service.create(
            f.draft(
                deliveries = listOf(
                    f.delivery(),
                    f.delivery(),
                    f.delivery(family),
                    NotificationDraft.Delivery(instance = "not-an-id"),
                ),
            ),
            alice,
        )

        assertEquals(
            mapOf(
                "deliveries[1].instance" to "Club channel is chosen more than once",
                "deliveries[2].instance" to "Family channel belongs to another group",
                "deliveries[3].instance" to "is not a delivery instance",
            ),
            problems(result),
        )
    }

    @Test
    fun `moving a notification to another group needs that group's instances`() {
        val n = created()

        val moved = service.update(n.id, f.draft(group = "notify_family"), admin)

        assertEquals(mapOf("deliveries[0].instance" to "Club channel belongs to another group"), problems(moved))
    }

    @Test
    fun `members see their group's notifications, others do not, admins see all`() {
        val n = created()

        assertEquals(n, service.find(n.id, alice))
        assertNull(service.find(n.id, bob))
        assertEquals(n, service.find(n.id, admin))
        assertEquals(listOf(n), service.list(alice))
        assertEquals(emptyList(), service.list(bob))
        assertIs<NotificationResult.NotFound>(service.pause(n.id, bob))
    }

    @Test
    fun `a member may only create in their own groups, an admin in any notification group`() {
        assertEquals("you are not a member of it", problems(service.create(f.draft(group = "notify_family"), alice))["group"])
        assertEquals("is not a notification group", problems(service.create(f.draft(group = "homelab_users"), alice))["group"])
        val family = f.instance(group = "notify_family", name = "Family channel")
        assertIs<NotificationResult.Saved>(service.create(f.draft(group = "notify_family", deliveries = listOf(f.delivery(family))), admin))
    }

    @Test
    fun `a pattern is stored in its canonical words, its start fixed at the day it was written`() {
        // The test clock stands at Tuesday 10 November 2026.
        val n = created(f.draft(kind = ScheduleKind.PATTERN, runAt = null).copy(pattern = "every other tuesday at 6pm"))

        assertEquals("every 2 weeks on tuesday at 18:00 starting 2026-11-10", n.schedule.pattern)
        assertEquals(Instant.parse("2026-11-10T23:00:00Z"), n.nextRunAt)

        // A week later, saving it again keeps it counting from the 10th, not from today.
        f.clock.advance(Duration.ofDays(7))
        val edited = assertIs<NotificationResult.Saved>(
            service.update(n.id, f.draft(kind = ScheduleKind.PATTERN, runAt = null).copy(title = "Renamed", pattern = n.schedule.pattern!!), alice),
        ).notification
        assertEquals(Instant.parse("2026-11-24T23:00:00Z"), edited.nextRunAt)
    }

    @Test
    fun `a pattern that cannot be read, or has ended, is refused against its field`() {
        val draft = f.draft(kind = ScheduleKind.PATTERN, runAt = null)

        assertEquals("Say what time, e.g. “at 6pm”.", problems(service.create(draft.copy(pattern = "every week on tuesday"), alice))["pattern"])
        assertEquals("Its end date has passed.", problems(service.create(draft.copy(pattern = "every day at 9am starting 2026-01-01 until 2026-02-01"), alice))["pattern"])
        assertTrue(problems(service.create(draft, alice)).containsKey("pattern"))
    }

    @Test
    fun `a reader may see but not change`() {
        val n = created()

        assertEquals(n, service.find(n.id, reader))
        assertIs<NotificationResult.Forbidden>(service.update(n.id, f.draft(), reader))
        assertIs<NotificationResult.Forbidden>(service.delete(n.id, reader))
    }

    @Test
    fun `pausing clears the next run and resuming picks up from now`() {
        val n = created(f.draft(kind = ScheduleKind.RECURRING, runAt = null, cron = "0 18 * * *"))
        assertEquals(Instant.parse("2026-11-10T23:00:00Z"), n.nextRunAt)

        val paused = assertIs<NotificationResult.Saved>(service.pause(n.id, alice)).notification
        assertEquals(NotificationStatus.PAUSED, paused.status)
        assertNull(paused.nextRunAt)

        f.clock.advance(Duration.ofDays(3))
        val resumed = assertIs<NotificationResult.Saved>(service.resume(n.id, alice)).notification
        assertEquals(Instant.parse("2026-11-13T23:00:00Z"), resumed.nextRunAt, "missed runs are not sent")
        assertEquals(
            listOf(AuditEventType.RESUMED, AuditEventType.PAUSED, AuditEventType.CREATED),
            f.audit.history(alice, AuditQuery(notificationId = n.id)).map { it.type },
        )
    }

    @Test
    fun `a one-off whose time passed while paused cannot be resumed`() {
        val n = created()
        service.pause(n.id, alice)
        f.clock.advance(Duration.ofDays(2))

        assertTrue("schedule" in problems(service.resume(n.id, alice)))
    }

    @Test
    fun `editing keeps a paused notification paused`() {
        val n = created()
        service.pause(n.id, alice)

        val edited = assertIs<NotificationResult.Saved>(service.update(n.id, f.draft().copy(title = "Renamed"), alice)).notification
        assertEquals(NotificationStatus.PAUSED, edited.status)
        assertNull(edited.nextRunAt)
        assertEquals("changed title; paused", f.audit.history(alice, AuditQuery(notificationId = n.id)).first().detail)
    }

    @Test
    fun `only overridable fields are kept, and only where they differ from the instance`() {
        val loud = f.instance(name = "Loud channel", config = mapOf("channel" to "general", "loud" to "true"))

        val n = created(
            f.draft(
                deliveries = listOf(
                    // channel and token are the instance's: a notification cannot redirect or re-key it.
                    f.delivery(overrides = mapOf("loud" to "true", "channel" to "elsewhere", "token" to "stolen")),
                    f.delivery(loud, overrides = mapOf("loud" to "true")),
                ),
            ),
        )

        assertEquals(listOf(mapOf("loud" to "true"), emptyMap()), n.deliveries.map { it.overrides })
    }

    @Test
    fun `a delivery keeps its id when re-saved, and an unchanged one is no change`() {
        val n = created()
        val id = n.deliveries.single().id.toString()

        val again = assertIs<NotificationResult.Saved>(service.update(n.id, f.draft(deliveries = listOf(f.delivery().copy(id = id))), alice)).notification

        assertEquals(n.deliveries.single().id, again.deliveries.single().id)
        assertTrue(f.audit.history(alice, AuditQuery(notificationId = n.id)).first().detail.startsWith("next run"))
    }

    @Test
    fun `a recurring schedule with an end date must still have a run left`() {
        val past = f.draft(kind = ScheduleKind.RECURRING, runAt = null, cron = "0 18 * * *").copy(until = LocalDate.of(2026, 11, 1))
        assertEquals("must not have passed", problems(service.create(past, alice))["until"])

        val never = f.draft(kind = ScheduleKind.RECURRING, runAt = null, cron = "0 18 1 1 *").copy(until = LocalDate.of(2026, 12, 31))
        assertEquals("never runs before the end date", problems(service.create(never, alice))["cron"])
    }

    @Test
    fun `a completed one-off can be edited without choosing a new time`() {
        val n = created()
        f.clock.advance(Duration.ofDays(2))
        f.dispatch.runDue()
        assertEquals(NotificationStatus.COMPLETED, service.find(n.id, alice)?.status)

        val edited = assertIs<NotificationResult.Saved>(service.update(n.id, f.draft().copy(subject = "Typo fixed"), alice)).notification
        assertEquals(NotificationStatus.COMPLETED, edited.status)

        val again = assertIs<NotificationResult.Saved>(service.update(n.id, f.draft(runAt = LocalDateTime.of(2026, 11, 20, 9, 0)), alice)).notification
        assertEquals(NotificationStatus.ACTIVE, again.status, "a new time sends it again")
    }

    @Test
    fun `deleting keeps the history`() {
        val n = created()
        assertIs<NotificationResult.Saved>(service.delete(n.id, alice))

        assertNull(service.find(n.id, alice))
        assertEquals(listOf(AuditEventType.DELETED, AuditEventType.CREATED), f.audit.history(alice, AuditQuery(notificationId = n.id)).map { it.type })
    }
}
