/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.DispatchTrigger
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.NotificationResult
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.support.Fixtures
import io.brulejr.groupnotify.support.RecordingPlugin
import io.brulejr.groupnotify.support.TestUsers.alice
import io.brulejr.groupnotify.support.TestUsers.bob
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DispatchServiceTest {

    private val plugin = RecordingPlugin()
    private val f = Fixtures(plugin)

    private fun create(draft: NotificationDraft = f.draft()) =
        assertIs<NotificationResult.Saved>(f.notifications.create(draft, alice)).notification

    private fun history(id: java.util.UUID) = f.audit.history(alice, AuditQuery(notificationId = id)).reversed()

    @Test
    fun `nothing is sent before it is due`() {
        create()

        assertEquals(0, f.dispatch.runDue())
        assertTrue(plugin.sent.isEmpty())
    }

    @Test
    fun `a due one-off is sent with its placeholders filled, then completes`() {
        val n = create()
        f.clock.now = Instant.parse("2026-11-11T23:00:10Z")

        assertEquals(1, f.dispatch.runDue())

        val (message, target) = plugin.sent.single()
        assertEquals("Meeting on Wednesday", message.subject)
        assertTrue(message.html.contains("<strong>6:00 PM EST</strong>"), message.html)
        assertEquals("general", target["channel"])
        val stored = f.notifications.find(n.id, alice)!!
        assertEquals(NotificationStatus.COMPLETED, stored.status)
        assertNull(stored.nextRunAt)
        val events = history(n.id)
        assertEquals(listOf(AuditEventType.CREATED, AuditEventType.SENT, AuditEventType.COMPLETED), events.map { it.type })
        assertEquals("Club channel: #general", events[1].target)
        assertEquals(DispatchTrigger.SCHEDULE, events[1].trigger)
        assertEquals(0, f.dispatch.runDue(), "a completed notification is not sent again")
    }

    @Test
    fun `a recurring notification moves on to its next run`() {
        val n = create(f.draft(kind = ScheduleKind.RECURRING, runAt = null, cron = "0 18 * * *"))
        f.clock.now = Instant.parse("2026-11-10T23:00:05Z")

        f.dispatch.runDue()

        val stored = f.notifications.find(n.id, alice)!!
        assertEquals(Instant.parse("2026-11-11T23:00:00Z"), stored.nextRunAt)
        assertEquals(NotificationStatus.ACTIVE, stored.status)
        assertEquals(f.clock.now, stored.lastRunAt)
    }

    @Test
    fun `a run missed by more than the grace is skipped, and sent once, not once per missed run`() {
        val n = create(f.draft(kind = ScheduleKind.RECURRING, runAt = null, cron = "0 18 * * *"))
        f.clock.now = Instant.parse("2026-11-13T12:00:00Z")

        f.dispatch.runDue()

        assertTrue(plugin.sent.isEmpty())
        assertEquals(AuditEventType.SKIPPED, history(n.id).last().type)
        assertEquals(Instant.parse("2026-11-13T23:00:00Z"), f.notifications.find(n.id, alice)!!.nextRunAt)
    }

    @Test
    fun `a paused notification is not sent`() {
        val n = create()
        f.notifications.pause(n.id, alice)
        f.clock.advance(Duration.ofDays(2))

        assertEquals(0, f.dispatch.runDue())
    }

    @Test
    fun `a failure is retried when the plugin says so, and recorded with its attempts`() {
        val flaky = RecordingPlugin("flaky", DeliveryResult.Failed("timeout", retryable = true), DeliveryResult.Delivered("ok", "msg-1"))
        val f = Fixtures(flaky)
        val n = assertIs<NotificationResult.Saved>(
            f.notifications.create(f.draft(deliveries = listOf(f.delivery(f.instance(plugin = "flaky")))), alice),
        ).notification
        f.clock.now = Instant.parse("2026-11-11T23:00:00Z")

        f.dispatch.runDue()

        val sent = f.audit.history(alice, AuditQuery(notificationId = n.id, types = setOf(AuditEventType.SENT))).single()
        assertEquals(2, sent.attempts)
        assertEquals("msg-1", sent.reference)
    }

    @Test
    fun `a delivery that fails does not stop the others`() {
        val broken = RecordingPlugin("broken", DeliveryResult.Failed("bad address"))
        val f = Fixtures(plugin, broken)
        val n = assertIs<NotificationResult.Saved>(
            f.notifications.create(
                f.draft(
                    deliveries = listOf(
                        f.delivery(f.instance(name = "Broken", plugin = "broken")),
                        f.delivery(),
                    ),
                ),
                alice,
            ),
        ).notification
        f.clock.now = Instant.parse("2026-11-11T23:00:00Z")

        f.dispatch.runDue()

        assertEquals(1, plugin.sent.size)
        val types = f.audit.history(alice, AuditQuery(notificationId = n.id)).map { it.type }
        assertTrue(AuditEventType.FAILED in types && AuditEventType.SENT in types)
        assertEquals(1, f.dispatch.stats().deliveriesFailed)
    }

    @Test
    fun `send now leaves the schedule alone, and can test one delivery`() {
        val n = create(
            f.draft(
                deliveries = listOf(
                    f.delivery(f.instance(name = "A", config = mapOf("channel" to "a"))),
                    f.delivery(f.instance(name = "B", config = mapOf("channel" to "b"))),
                ),
            ),
        )

        val all = assertIs<SendResult.Sent>(f.dispatch.sendNow(n.id, alice))
        assertEquals(2, all.reports.size)
        val one = assertIs<SendResult.Sent>(f.dispatch.sendNow(n.id, alice, n.deliveries[1].id))
        assertEquals("B: #b", one.reports.single().summary)

        assertEquals(n.nextRunAt, f.notifications.find(n.id, alice)!!.nextRunAt)
        assertEquals(DispatchTrigger.MANUAL, history(n.id).last().trigger)
        assertEquals("alice", history(n.id).last().actor)
        assertIs<SendResult.NotFound>(f.dispatch.sendNow(n.id, bob))
    }

    @Test
    fun `a disabled delivery is skipped`() {
        val n = create(f.draft(deliveries = listOf(f.delivery(enabled = false))))

        assertIs<SendResult.NothingToSend>(f.dispatch.sendNow(n.id, alice))
    }

    @Test
    fun `the plugin gets the instance's configuration, secrets opened, with the notification's overrides`() {
        val n = create(f.draft(deliveries = listOf(f.delivery(overrides = mapOf("loud" to "true")))))

        f.dispatch.sendNow(n.id, alice)

        assertEquals(mapOf("channel" to "general", "token" to "s3cret", "loud" to "true"), plugin.sent.single().second.values)
        val sent = history(n.id).last()
        assertEquals("Club channel: #general", sent.target)
        assertTrue("s3cret" !in sent.target.orEmpty() + sent.detail)
    }

    @Test
    fun `a change to an instance's default reaches the notifications that did not override it`() {
        val n = create()
        f.instances.update(
            f.club.id,
            io.brulejr.groupnotify.instance.InstanceDraft("notify_club", "recording", "Club channel", mapOf("channel" to "news", "loud" to "true")),
            io.brulejr.groupnotify.support.TestUsers.admin,
        )

        f.dispatch.sendNow(n.id, alice)

        assertEquals(mapOf("channel" to "news", "token" to "s3cret", "loud" to "true"), plugin.sent.single().second.values)
    }

    @Test
    fun `a secret that cannot be opened fails the delivery without calling the plugin`() {
        val n = create()
        // As if sealed under another key.
        f.instanceStore.save(f.club.copy(config = f.club.config + ("token" to io.brulejr.groupnotify.instance.SecretCipher(null).seal("x"))))

        val report = assertIs<SendResult.Sent>(f.dispatch.sendNow(n.id, alice)).reports.single()

        assertTrue(plugin.sent.isEmpty())
        assertEquals("a secret of Club channel cannot be decrypted; an admin must re-enter it", report.outcome.detail)
    }
}
