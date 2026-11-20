/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.postgres

import io.brulejr.groupnotify.event.Event
import io.brulejr.groupnotify.event.EventProperty
import io.brulejr.groupnotify.event.OccurrenceNote
import io.brulejr.groupnotify.event.PendingReminder
import io.brulejr.groupnotify.event.Reminder
import io.brulejr.groupnotify.event.ReminderTiming
import io.brulejr.groupnotify.event.ReminderUnit
import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.instance.postgres.PostgresInstanceStore
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationDelivery
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.Schedule
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.platform.PostgresTestDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresEventStoreTest {

    private val json = JsonMapper.builder().build()
    private val store = PostgresEventStore(PostgresTestDatabase.jdbc, PostgresTestDatabase.transactions, json)
    private val instances = PostgresInstanceStore(PostgresTestDatabase.jdbc, json)
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
    private lateinit var instance: PluginInstance

    private fun event(next: Instant? = now.minusSeconds(5)): Event {
        val reminder = Reminder(
            UUID.randomUUID(), 1, ReminderUnit.HOURS, ReminderTiming.BEFORE, subject = "Soon", body = "**In an hour**",
            deliveries = listOf(NotificationDelivery(UUID.randomUUID(), instance.id, mapOf("body" to "text")), NotificationDelivery(UUID.randomUUID(), instance.id, enabled = false)),
        )
        val occurrence = now.plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS)
        return Event(
            id = UUID.randomUUID(), group = "notify_club", title = "Meeting", subject = "At {{event.location}}", body = "b",
            format = BodyFormat.MARKDOWN,
            schedule = Schedule(ScheduleKind.PATTERN, ZoneId.of("America/New_York"), pattern = "every 1 week on tuesday at 19:00 starting 2026-11-10"),
            properties = listOf(EventProperty("zebra", "z"), EventProperty("location", "Town hall")),
            reminders = listOf(
                reminder,
                Reminder(UUID.randomUUID(), 0, ReminderUnit.MINUTES, ReminderTiming.AT_START, enabled = false, deliveries = listOf(NotificationDelivery(UUID.randomUUID(), instance.id))),
            ),
            notes = mapOf(occurrence to OccurrenceNote("<p>Guest <strong>speaker</strong></p>", BodyFormat.HTML)),
            status = NotificationStatus.ACTIVE,
            next = next?.let { PendingReminder(it, occurrence, reminder.id) },
            lastSentAt = null, createdBy = "alice", createdAt = now, updatedBy = "alice", updatedAt = now,
        )
    }

    @BeforeEach
    fun reset() {
        PostgresTestDatabase.reset()
        instance = PluginInstance(UUID.randomUUID(), "notify_club", "log", "Club log", mapOf("body" to "subject"), "root", now, "root", now)
            .also(instances::save)
    }

    @Test
    fun `an event comes back as saved, with its properties in order, reminders, notes, deliveries and next reminder`() {
        val e = event()
        store.save(e)

        assertEquals(e, store.find(e.id))
        assertEquals(listOf("zebra", "location"), store.find(e.id)!!.properties.map { it.name })
    }

    @Test
    fun `due events are moved on only if still due when expected`() {
        val e = event()
        store.save(e)
        assertEquals(listOf(e.id), store.due(now, 10).map { it.id })

        assertTrue(store.advance(e.id, e.nextRunAt!!, null, NotificationStatus.COMPLETED, now))
        assertFalse(store.advance(e.id, e.nextRunAt!!, null, NotificationStatus.COMPLETED, now), "a second run of the same reminder loses")
        assertEquals(NotificationStatus.COMPLETED, store.find(e.id)!!.status)
        assertTrue(store.due(now, 10).isEmpty())
    }

    @Test
    fun `an instance an event uses cannot be deleted, and deleting the event frees it`() {
        val e = event()
        store.save(e)

        assertFailsWith<DataIntegrityViolationException> { instances.delete(instance.id) }
        assertTrue(store.delete(e.id))
        assertTrue(instances.delete(instance.id))
    }
}
