/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.DispatchTrigger
import io.brulejr.groupnotify.instance.InstanceDraft
import io.brulejr.groupnotify.instance.InstanceResult
import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.notification.NotificationResult
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.support.Fixtures
import io.brulejr.groupnotify.support.RecordingPlugin
import io.brulejr.groupnotify.support.TestUsers.admin
import io.brulejr.groupnotify.support.TestUsers.alice
import io.brulejr.groupnotify.support.TestUsers.bob
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlertServiceTest {

    private val announcements = RecordingPlugin("recording", DeliveryResult.Failed("401 Unauthorized"))
    private val organisers = RecordingPlugin("alerting")
    private val f = Fixtures(announcements, organisers, alertDatafill = AlertDatafill(consoleUrl = "https://notify.example.org/"))

    private val list: PluginInstance = f.instance(name = "Club groups.io")
    private val channel: PluginInstance = alertsInstance()
    private val message = UUID.randomUUID()

    private fun alertsInstance(group: String = "notify_club", name: String = "Organisers") = assertIs<InstanceResult.Saved>(
        f.instances.create(InstanceDraft(group, "alerting", name, mapOf("channel" to "organisers"), alerts = true), admin),
    ).instance

    private fun record(type: AuditEventType, instance: UUID? = list.id, trigger: DispatchTrigger = DispatchTrigger.SCHEDULE, detail: String = "401 Unauthorized") =
        f.audit.record(
            AuditEvent(
                occurredAt = f.clock.now, type = type, actor = AuditEvent.SCHEDULER, notificationId = message,
                notificationTitle = "Thursday net *reminder*", group = "notify_club", plugin = "recording",
                target = "Club groups.io: #general", trigger = trigger, detail = detail, attempts = 3, instanceId = instance,
            ),
        )

    private fun alertsRecorded() = f.audit.find(AuditQuery(types = setOf(AuditEventType.ALERTED, AuditEventType.RECOVERED), limit = 100))

    @Test
    fun `a scheduled delivery that fails is posted to the group's alerts instance, linked and escaped`() {
        record(AuditEventType.FAILED)

        val (alert, target) = organisers.sent.single()
        assertEquals("organisers", target["channel"])
        assertEquals("⚠ Thursday net *reminder* didn't go out", alert.subject)
        assertTrue("**Thursday net \\*reminder\\*** didn't go out through *Club groups.io*: 401 Unauthorized (3 attempts)." in alert.markdown, alert.markdown)
        assertTrue("https://notify.example.org/ui/messages/$message" in alert.text)
        val recorded = alertsRecorded().single()
        assertEquals(AuditEventType.ALERTED, recorded.type)
        assertEquals(list.id, recorded.instanceId)
        assertEquals("Organisers", recorded.target)
    }

    @Test
    fun `further failures are held back for the quiet period, then counted into the next alert`() {
        record(AuditEventType.FAILED)
        f.clock.advance(Duration.ofMinutes(10))
        record(AuditEventType.FAILED)
        f.clock.advance(Duration.ofMinutes(10))
        record(AuditEventType.FAILED)
        assertEquals(1, organisers.sent.size)

        f.clock.advance(Duration.ofMinutes(45))
        record(AuditEventType.FAILED)
        assertEquals(2, organisers.sent.size)
        assertTrue("2 more failures through Club groups.io since the last alert." in organisers.sent.last().first.markdown)
    }

    @Test
    fun `once a failing instance sends again, the alerts instance is told, and only once`() {
        record(AuditEventType.SENT)
        assertTrue(organisers.sent.isEmpty(), "nothing had failed")

        record(AuditEventType.FAILED)
        record(AuditEventType.SENT, detail = "posted")
        record(AuditEventType.SENT, detail = "posted")

        assertEquals(listOf("⚠ Thursday net *reminder* didn't go out", "✅ Club groups.io is sending again"), organisers.sent.map { it.first.subject })
        assertEquals(AuditEventType.RECOVERED, alertsRecorded().first().type)
        // A new failure after recovering is alerted at once, quiet period or not.
        record(AuditEventType.FAILED)
        assertEquals(3, organisers.sent.size)
    }

    @Test
    fun `a skipped run is alerted, once per quiet period for the message`() {
        record(AuditEventType.SKIPPED, instance = null, detail = "due at 18:00, missed by 90 min (grace 60 min)")
        record(AuditEventType.SKIPPED, instance = null, detail = "due at 18:00, missed by 95 min (grace 60 min)")

        val alert = organisers.sent.single().first
        assertEquals("⚠ Thursday net *reminder* was skipped", alert.subject)
        assertTrue("was not sent: due at 18:00, missed by 90 min" in alert.markdown)
    }

    @Test
    fun `nothing is alerted for sends someone pressed, through the instance that failed, or about alerts`() {
        record(AuditEventType.FAILED, trigger = DispatchTrigger.MANUAL)
        assertTrue(organisers.sent.isEmpty(), "whoever pressed Send now sees the failure")

        // The alerts instance itself failing is not posted to itself.
        record(AuditEventType.FAILED, instance = channel.id)
        assertTrue(organisers.sent.isEmpty())
    }

    @Test
    fun `alerts go to every instance of the group marked for them, and to no other group's`() {
        val second = alertsInstance(name = "Second channel")
        alertsInstance(group = "notify_family", name = "Family organisers")

        record(AuditEventType.FAILED)

        assertEquals(2, organisers.sent.size)
        assertEquals(setOf("Organisers", "Second channel"), alertsRecorded().mapNotNull { it.target }.toSet())
        assertTrue(second.alerts)
    }

    @Test
    fun `an alert that cannot be sent is recorded as such and still keeps the next one quiet`() {
        val f = Fixtures(announcements, RecordingPlugin("alerting", DeliveryResult.Failed("channel gone")))
        assertIs<InstanceResult.Saved>(f.instances.create(InstanceDraft("notify_club", "alerting", "Organisers", mapOf("channel" to "x"), alerts = true), admin))
        val failing = f.instance(name = "Club groups.io")
        val event = AuditEvent(
            occurredAt = f.clock.now, type = AuditEventType.FAILED, actor = AuditEvent.SCHEDULER, notificationId = message,
            notificationTitle = "Net", group = "notify_club", trigger = DispatchTrigger.SCHEDULE, detail = "401", instanceId = failing.id,
        )
        f.audit.record(event)
        f.audit.record(event)

        val recorded = f.audit.find(AuditQuery(types = setOf(AuditEventType.ALERTED))).single()
        assertTrue("not sent: channel gone" in recorded.detail)
    }

    @Test
    fun `a failure through the dispatcher names its instance, so it is alerted`() {
        val n = assertIs<NotificationResult.Saved>(f.notifications.create(f.draft(deliveries = listOf(f.delivery(list))), alice)).notification
        f.clock.now = Instant.parse("2026-11-11T23:00:00Z")

        f.dispatch.runDue()

        val failed = f.audit.find(AuditQuery(notificationId = n.id, types = setOf(AuditEventType.FAILED))).single()
        assertEquals(list.id, failed.instanceId)
        assertTrue("**Meeting reminder** didn't go out through *Club groups.io*: 401 Unauthorized" in organisers.sent.single().first.markdown)
    }

    @Test
    fun `a test alert is sent for those who manage the group, and recorded without quieting real ones`() {
        assertIs<AlertTestResult.Sent>(f.alerts.test(channel.id, alice))
        assertEquals("🔔 Test alert for ${f.groups.displayName("notify_club")}", organisers.sent.single().first.subject)
        assertIs<AlertTestResult.NotFound>(f.alerts.test(channel.id, bob))

        record(AuditEventType.FAILED, instance = list.id)
        assertEquals(2, organisers.sent.size, "the test did not count as an alert about the failing instance")
    }

    @Test
    fun `alerts can be turned off`() {
        val f = Fixtures(announcements, organisers, alertDatafill = AlertDatafill(enabled = false))
        f.instances.create(InstanceDraft("notify_club", "alerting", "Organisers", mapOf("channel" to "x"), alerts = true), admin)
        val failing = f.instance(name = "Club groups.io")
        f.audit.record(
            AuditEvent(
                occurredAt = f.clock.now, type = AuditEventType.FAILED, actor = AuditEvent.SCHEDULER, notificationId = message,
                notificationTitle = "Net", group = "notify_club", trigger = DispatchTrigger.SCHEDULE, detail = "401", instanceId = failing.id,
            ),
        )
        assertTrue(organisers.sent.isEmpty())
    }
}
