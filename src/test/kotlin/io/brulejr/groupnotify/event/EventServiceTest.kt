/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.DispatchTrigger
import io.brulejr.groupnotify.dispatch.SendResult
import io.brulejr.groupnotify.instance.InstanceResult
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.support.Fixtures
import io.brulejr.groupnotify.support.RecordingPlugin
import io.brulejr.groupnotify.support.TestUsers.admin
import io.brulejr.groupnotify.support.TestUsers.alice
import io.brulejr.groupnotify.support.TestUsers.bob
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventServiceTest {

    private val plugin = RecordingPlugin()
    private val f = Fixtures(plugin)
    private val service = f.events

    private fun created(draft: EventDraft = f.eventDraft()) = assertIs<EventResult.Saved>(service.create(draft, alice)).event

    private fun problems(result: EventResult) = assertIs<EventResult.Invalid>(result).problems.associate { it.field to it.reason }

    // The test clock stands at Tuesday 10 November 2026, 07:00 in New York.
    private val meetingStart = Instant.parse("2026-11-11T00:00:00Z")

    @Test
    fun `an event's next reminder is planned from its schedule, and its creation recorded against it`() {
        val e = created()

        // Tonight's meeting is 12 hours away: the day-before reminder has passed, the hour-before is next.
        assertEquals(PendingReminder(Instant.parse("2026-11-10T23:00:00Z"), meetingStart, e.reminders[1].id), e.next)
        assertEquals(NotificationStatus.ACTIVE, e.status)
        val history = f.audit.history(alice, AuditQuery(eventId = e.id))
        assertEquals(listOf(AuditEventType.CREATED), history.map { it.type })
        assertNull(history.single().notificationId)
    }

    @Test
    fun `a group's members manage its events, and nobody else sees them`() {
        val e = created()

        assertNull(service.find(e.id, bob))
        assertIs<EventResult.NotFound>(service.update(e.id, f.eventDraft(), bob))
        assertIs<EventResult.NotFound>(service.delete(e.id, bob))
        assertEquals("you are not a member of it", problems(service.create(f.eventDraft(), bob))["group"])
        assertIs<EventResult.Saved>(service.update(e.id, f.eventDraft(), admin))
        assertIs<EventResult.Saved>(service.delete(e.id, alice))
    }

    @Test
    fun `properties, reminders and deliveries are checked against their inputs`() {
        val family = f.instance(group = "notify_family", name = "Family channel")
        val draft = f.eventDraft(
            reminders = listOf(
                EventDraft.Reminder(amount = "x"),
                EventDraft.Reminder(amount = "2", unit = ReminderUnit.HOURS),
                EventDraft.Reminder(amount = "2", unit = ReminderUnit.HOURS),
                EventDraft.Reminder(amount = "500", unit = ReminderUnit.DAYS),
            ),
            deliveries = listOf(f.delivery(family)),
        ).copy(
            properties = listOf(EventProperty("Location", "a"), EventProperty("location", "b"), EventProperty("2nd", "c"), EventProperty("when", "d")),
        )

        assertEquals(
            mapOf(
                "properties[1].name" to "is given twice",
                "properties[2].name" to "must be letters, digits and _, starting with a letter, e.g. location",
                "properties[3].name" to "is one of the event's own placeholders: choose another name",
                "reminders[0].amount" to "must be a whole number",
                "reminders[2].amount" to "is the same as another reminder's",
                "reminders[3].amount" to "must be 1 to 90 days",
                "deliveries[0].instance" to "Family channel belongs to another group",
            ),
            problems(service.create(draft, alice)),
        )
        assertEquals("add at least one reminder", problems(service.create(f.eventDraft(reminders = emptyList()), alice))["reminders"])
    }

    @Test
    fun `a reminder's message fills the event's placeholders, and uses its own words when it has them`() {
        val e = created()
        val dayBefore = e.reminders[0]
        val hourBefore = e.reminders[1]

        val full = service.message(e, meetingStart, dayBefore, "Radio Club", dayBefore.sendAt(meetingStart, e.schedule.zone))
        assertEquals("Meeting Tuesday at Town hall", full.subject)
        assertEquals("See you in 1 day at **Town hall**.", full.markdown)

        val own = service.message(e, meetingStart, hourBefore, "Radio Club", hourBefore.sendAt(meetingStart, e.schedule.zone))
        assertEquals("Starting in 1 hour", own.subject)
        // No body of its own: the event's.
        assertEquals("See you in 1 hour at **Town hall**.", own.markdown)
    }

    @Test
    fun `an occurrence's note is added after the body, or placed where the body asks`() {
        val e = created()
        val noted = assertIs<EventResult.Saved>(service.setNote(e.id, meetingStart, "This week: *guest speaker*", BodyFormat.MARKDOWN, alice)).event
        val reminder = noted.reminders[0]

        val appended = service.message(noted, meetingStart, reminder, "Radio Club", Instant.parse("2026-11-10T12:00:00Z"))
        assertEquals("See you in 1 day at **Town hall**.\n\nThis week: *guest speaker*", appended.markdown)
        // Another occurrence has no note.
        val nextWeek = Instant.parse("2026-11-18T00:00:00Z")
        assertEquals("See you in 1 day at **Town hall**.", service.message(noted, nextWeek, reminder, "Radio Club", nextWeek).markdown)

        val placed = assertIs<EventResult.Saved>(
            service.update(e.id, f.eventDraft().copy(body = "Agenda: {{occurrence.note}}. At {{event.location}}."), alice),
        ).event
        assertEquals("Agenda: This week: *guest speaker*. At Town hall.", service.message(placed, meetingStart, reminder, "Radio Club", meetingStart).markdown)
    }

    @Test
    fun `a note in either editor is converted to each message's format`() {
        // A rich-text note on a Markdown event.
        val e = created()
        val noted = assertIs<EventResult.Saved>(service.setNote(e.id, meetingStart, "<p>Bring <strong>QSL cards</strong></p>", BodyFormat.HTML, alice)).event
        val reminder = noted.reminders[0]
        assertEquals("See you in 1 day at **Town hall**.\n\nBring **QSL cards**", service.message(noted, meetingStart, reminder, "Radio Club", meetingStart).markdown)

        // A Markdown note on a rich-text event, placed where its body says.
        val html = assertIs<EventResult.Saved>(
            service.update(e.id, f.eventDraft().copy(format = BodyFormat.HTML, body = "<p>At {{event.location}}</p>{{occurrence.note}}"), alice),
        ).event
        val markdownNote = assertIs<EventResult.Saved>(service.setNote(html.id, meetingStart, "Bring **QSL cards**", BodyFormat.MARKDOWN, alice)).event
        assertEquals("<p>At Town hall</p><p>Bring <strong>QSL cards</strong></p>", service.message(markdownNote, meetingStart, reminder, "Radio Club", meetingStart).html)

        // An empty rich-text note is no note.
        assertTrue(assertIs<EventResult.Saved>(service.setNote(e.id, meetingStart, "<p><br></p>", BodyFormat.HTML, alice)).event.notes.isEmpty())
    }

    @Test
    fun `a note belongs to one of the event's occurrences`() {
        val e = created()

        assertEquals("is not one of this event's occurrences", problems(service.setNote(e.id, Instant.parse("2026-11-11T01:00:00Z"), "x", BodyFormat.MARKDOWN, alice))["occurrence"])
        val removed = assertIs<EventResult.Saved>(service.setNote(e.id, meetingStart, "", BodyFormat.MARKDOWN, alice)).event
        assertTrue(removed.notes.isEmpty())
    }

    @Test
    fun `pausing holds its reminders, resuming picks up from now, editing keeps its reminders' ids`() {
        val e = created()

        val paused = assertIs<EventResult.Saved>(service.pause(e.id, alice)).event
        assertNull(paused.next)
        f.clock.advance(Duration.ofDays(7))
        val resumed = assertIs<EventResult.Saved>(service.resume(e.id, alice)).event
        assertEquals(Instant.parse("2026-11-17T23:00:00Z"), resumed.nextRunAt)

        val ids = e.reminders.map { it.id.toString() }
        val draft = f.eventDraft().let { d -> d.copy(reminders = d.reminders.mapIndexed { i, r -> r.copy(id = ids[i]) }) }
        val edited = assertIs<EventResult.Saved>(service.update(e.id, draft, alice)).event
        assertEquals(e.reminders.map { it.id }, edited.reminders.map { it.id })
    }

    @Test
    fun `an instance an event uses cannot be deleted`() {
        created()

        assertEquals(InstanceResult.InUse(listOf("event: Club meeting")), f.instances.delete(f.club.id, alice))
    }

    // ---- sending ----

    @Test
    fun `a due reminder is sent with its words, recorded against the event, and the next planned`() {
        val e = created()
        f.clock.now = Instant.parse("2026-11-10T23:00:30Z")

        assertEquals(1, f.dispatch.runDue())

        assertEquals("Starting in 1 hour", plugin.sent.single().first.subject)
        val sent = f.audit.history(alice, AuditQuery(eventId = e.id, types = setOf(AuditEventType.SENT))).single()
        assertEquals(DispatchTrigger.SCHEDULE, sent.trigger)
        assertEquals("Club channel: #general", sent.target)
        // Next: the day-before reminder for next week's meeting.
        assertEquals(Instant.parse("2026-11-17T00:00:00Z"), service.get(e.id)!!.nextRunAt)
        assertEquals(0, f.dispatch.runDue())
    }

    @Test
    fun `a reminder missed by more than the grace is skipped, and the backlog is not sent`() {
        val e = created()
        f.clock.now = Instant.parse("2026-11-20T12:00:00Z")

        f.dispatch.runDue()

        assertTrue(plugin.sent.isEmpty())
        assertEquals(listOf(AuditEventType.SKIPPED), f.audit.history(alice, AuditQuery(eventId = e.id, types = setOf(AuditEventType.SKIPPED, AuditEventType.SENT))).map { it.type })
        assertTrue(service.get(e.id)!!.nextRunAt!! > f.clock.now)
    }

    @Test
    fun `send now sends the event's own message for its next occurrence, by hand`() {
        val e = created()

        val sent = assertIs<SendResult.Sent>(f.dispatch.sendEventNow(e.id, alice))

        assertEquals(1, sent.reports.size)
        assertEquals("Meeting Tuesday at Town hall", plugin.sent.single().first.subject)
        // From 07:00 to the 19:00 meeting.
        assertEquals("See you in 12 hours at **Town hall**.", plugin.sent.single().first.markdown)
        assertEquals(DispatchTrigger.MANUAL, f.audit.history(alice, AuditQuery(eventId = e.id)).first().trigger)
        assertIs<SendResult.NotFound>(f.dispatch.sendEventNow(e.id, bob))
        // Its planned reminders are left alone.
        assertEquals(e.next, service.get(e.id)?.next)
    }
}
