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
import io.brulejr.groupnotify.event.EventPort
import io.brulejr.groupnotify.event.EventProperty
import io.brulejr.groupnotify.event.OccurrenceNote
import io.brulejr.groupnotify.event.PendingReminder
import io.brulejr.groupnotify.event.Reminder
import io.brulejr.groupnotify.event.ReminderTiming
import io.brulejr.groupnotify.event.ReminderUnit
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationDelivery
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.Schedule
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.platform.getInstant
import io.brulejr.groupnotify.platform.toTimestamptz
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.support.TransactionOperations
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * PostgreSQL-backed [EventPort]: the `events` table, its properties, reminders and notes
 * as `jsonb`, and each reminder's deliveries in `event_deliveries`, which refer to their
 * instances by foreign key so an instance in use cannot be deleted.
 */
class PostgresEventStore(
    private val jdbc: JdbcClient,
    private val transactions: TransactionOperations,
    private val json: JsonMapper,
) : EventPort {

    override fun find(id: UUID): Event? = load("WHERE id = :id", mapOf("id" to id)).singleOrNull()

    override fun list(groups: Set<String>?): List<Event> = when {
        groups == null -> load("", emptyMap())
        groups.isEmpty() -> emptyList()
        else -> load("WHERE group_name IN (:groups)", mapOf("groups" to groups.toList()))
    }

    override fun save(event: Event) {
        transactions.executeWithoutResult {
            jdbc.sql(
                """
                INSERT INTO events (id, group_name, title, subject, body, body_format, schedule_kind, run_at, cron, pattern,
                                    zone, until, properties, reminders, notes, status, next_run_at, next_occurrence,
                                    next_reminder, last_sent_at, created_by, created_at, updated_by, updated_at)
                VALUES (:id, :group, :title, :subject, :body, :format, :kind, :runAt, :cron, :pattern,
                        :zone, :until, CAST(:properties AS jsonb), CAST(:reminders AS jsonb), CAST(:notes AS jsonb), :status,
                        :nextRunAt, :nextOccurrence, :nextReminder, :lastSentAt, :createdBy, :createdAt, :updatedBy, :updatedAt)
                ON CONFLICT (id) DO UPDATE
                SET group_name = EXCLUDED.group_name, title = EXCLUDED.title, subject = EXCLUDED.subject,
                    body = EXCLUDED.body, body_format = EXCLUDED.body_format, schedule_kind = EXCLUDED.schedule_kind,
                    run_at = EXCLUDED.run_at, cron = EXCLUDED.cron, pattern = EXCLUDED.pattern, zone = EXCLUDED.zone,
                    until = EXCLUDED.until, properties = EXCLUDED.properties, reminders = EXCLUDED.reminders,
                    notes = EXCLUDED.notes, status = EXCLUDED.status, next_run_at = EXCLUDED.next_run_at,
                    next_occurrence = EXCLUDED.next_occurrence, next_reminder = EXCLUDED.next_reminder,
                    last_sent_at = EXCLUDED.last_sent_at, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
                """,
            )
                .param("id", event.id)
                .param("group", event.group)
                .param("title", event.title)
                .param("subject", event.subject)
                .param("body", event.body)
                .param("format", event.format.name)
                .param("kind", event.schedule.kind.name)
                .param("runAt", event.schedule.runAt?.toTimestamptz())
                .param("cron", event.schedule.cron)
                .param("pattern", event.schedule.pattern)
                .param("zone", event.schedule.zone.id)
                .param("until", event.schedule.until?.toTimestamptz())
                .param("properties", json.writeValueAsString(event.properties.map { mapOf("name" to it.name, "value" to it.value) }))
                .param("reminders", json.writeValueAsString(event.reminders.map(::reminderJson)))
                .param("notes", json.writeValueAsString(event.notes.entries.associate { (at, n) -> at.toString() to mapOf("text" to n.text, "format" to n.format.name) }))
                .param("status", event.status.name)
                .param("nextRunAt", event.next?.sendAt?.toTimestamptz())
                .param("nextOccurrence", event.next?.occurrence?.toTimestamptz())
                .param("nextReminder", event.next?.reminder)
                .param("lastSentAt", event.lastSentAt?.toTimestamptz())
                .param("createdBy", event.createdBy)
                .param("createdAt", event.createdAt.toTimestamptz())
                .param("updatedBy", event.updatedBy)
                .param("updatedAt", event.updatedAt.toTimestamptz())
                .update()
            jdbc.sql("DELETE FROM event_deliveries WHERE event_id = :id").param("id", event.id).update()
            event.reminders.forEach { reminder ->
                reminder.deliveries.forEachIndexed { position, delivery ->
                    jdbc.sql(
                        """
                        INSERT INTO event_deliveries (id, event_id, reminder_id, position, instance_id, enabled, overrides)
                        VALUES (:id, :eventId, :reminderId, :position, :instance, :enabled, CAST(:overrides AS jsonb))
                        """,
                    )
                        .param("id", delivery.id)
                        .param("eventId", event.id)
                        .param("reminderId", reminder.id)
                        .param("position", position)
                        .param("instance", delivery.instance)
                        .param("enabled", delivery.enabled)
                        .param("overrides", json.writeValueAsString(delivery.overrides))
                        .update()
                }
            }
        }
    }

    override fun delete(id: UUID): Boolean = jdbc.sql("DELETE FROM events WHERE id = :id").param("id", id).update() > 0

    override fun due(now: Instant, limit: Int): List<Event> = load(
        "WHERE status = 'ACTIVE' AND next_run_at <= :now ORDER BY next_run_at LIMIT :limit",
        mapOf("now" to now.toTimestamptz(), "limit" to limit),
        ordered = true,
    )

    override fun advance(id: UUID, expected: Instant, next: PendingReminder?, status: NotificationStatus, sentAt: Instant): Boolean =
        jdbc.sql(
            """
            UPDATE events SET next_run_at = :nextRunAt, next_occurrence = :nextOccurrence, next_reminder = :nextReminder,
                              status = :status, last_sent_at = :sentAt
            WHERE id = :id AND status = 'ACTIVE' AND next_run_at = :expected
            """,
        )
            .param("id", id)
            .param("expected", expected.toTimestamptz())
            .param("nextRunAt", next?.sendAt?.toTimestamptz())
            .param("nextOccurrence", next?.occurrence?.toTimestamptz())
            .param("nextReminder", next?.reminder)
            .param("status", status.name)
            .param("sentAt", sentAt.toTimestamptz())
            .update() > 0

    override fun groups(): Set<String> =
        jdbc.sql("SELECT DISTINCT group_name FROM events").query { rs, _ -> rs.getString(1) }.list().toSortedSet()

    private fun load(clause: String, params: Map<String, Any?>, ordered: Boolean = false): List<Event> {
        val order = if (ordered) "" else " ORDER BY created_at, id"
        val rows = jdbc.sql("SELECT * FROM events $clause$order").params(params).query { rs, _ -> rs.toEvent() }.list()
        if (rows.isEmpty()) return emptyList()
        val deliveries = jdbc.sql("SELECT * FROM event_deliveries WHERE event_id IN (:ids) ORDER BY position")
            .param("ids", rows.map { it.id })
            .query { rs, _ ->
                rs.getObject("reminder_id", UUID::class.java) to NotificationDelivery(
                    id = rs.getObject("id", UUID::class.java),
                    instance = rs.getObject("instance_id", UUID::class.java),
                    overrides = json.readValue(rs.getString("overrides"), STRINGS),
                    enabled = rs.getBoolean("enabled"),
                )
            }
            .list()
            .groupBy({ it.first }, { it.second })
        return rows.map { e -> e.copy(reminders = e.reminders.map { r -> r.copy(deliveries = deliveries[r.id].orEmpty()) }) }
    }

    private fun ResultSet.toEvent(): Event {
        val nextRunAt = getInstant("next_run_at")
        return Event(
            id = getObject("id", UUID::class.java),
            group = getString("group_name"),
            title = getString("title"),
            subject = getString("subject"),
            body = getString("body"),
            format = BodyFormat.valueOf(getString("body_format")),
            schedule = Schedule(
                kind = ScheduleKind.valueOf(getString("schedule_kind")),
                zone = ZoneId.of(getString("zone")),
                runAt = getInstant("run_at"),
                cron = getString("cron"),
                until = getInstant("until"),
                pattern = getString("pattern"),
            ),
            properties = json.readValue(getString("properties"), MAPS).map { EventProperty(it.getValue("name").toString(), it["value"]?.toString().orEmpty()) },
            reminders = json.readValue(getString("reminders"), MAPS).map(::reminderOf),
            notes = json.readValue(getString("notes"), NOTES).entries.associate { (at, n) ->
                Instant.parse(at) to OccurrenceNote(n["text"].orEmpty(), BodyFormat.valueOf(n["format"] ?: BodyFormat.MARKDOWN.name))
            },
            status = NotificationStatus.valueOf(getString("status")),
            next = nextRunAt?.let { PendingReminder(it, checkNotNull(getInstant("next_occurrence")), getObject("next_reminder", UUID::class.java)) },
            lastSentAt = getInstant("last_sent_at"),
            createdBy = getString("created_by"),
            createdAt = checkNotNull(getInstant("created_at")),
            updatedBy = getString("updated_by"),
            updatedAt = checkNotNull(getInstant("updated_at")),
        )
    }

    private fun reminderJson(r: Reminder) = mapOf(
        "id" to r.id.toString(), "amount" to r.amount, "unit" to r.unit.name, "timing" to r.timing.name,
        "subject" to r.subject, "body" to r.body, "enabled" to r.enabled,
    )

    private fun reminderOf(m: Map<String, Any?>) = Reminder(
        id = UUID.fromString(m.getValue("id").toString()),
        amount = (m["amount"] as Number).toInt(),
        unit = ReminderUnit.valueOf(m.getValue("unit").toString()),
        timing = ReminderTiming.valueOf(m.getValue("timing").toString()),
        subject = m["subject"]?.toString().orEmpty(),
        body = m["body"]?.toString().orEmpty(),
        enabled = m["enabled"] as? Boolean ?: true,
    )

    private companion object {
        val STRINGS = object : TypeReference<Map<String, String>>() {}
        val MAPS = object : TypeReference<List<Map<String, Any?>>>() {}
        val NOTES = object : TypeReference<Map<String, Map<String, String>>>() {}
    }
}
