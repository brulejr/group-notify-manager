/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.postgres

import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.notification.NotificationDelivery
import io.brulejr.groupnotify.notification.NotificationPort
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
 * PostgreSQL-backed [NotificationPort]: the `notifications` table, with each one's
 * deliveries in `deliveries`, their targets as `jsonb`.
 */
class PostgresNotificationStore(
    private val jdbc: JdbcClient,
    private val transactions: TransactionOperations,
    private val json: JsonMapper,
) : NotificationPort {

    override fun find(id: UUID): Notification? = load("WHERE id = :id", mapOf("id" to id)).singleOrNull()

    override fun list(groups: Set<String>?): List<Notification> = when {
        groups == null -> load("", emptyMap())
        groups.isEmpty() -> emptyList()
        else -> load("WHERE group_name IN (:groups)", mapOf("groups" to groups.toList()))
    }

    override fun save(notification: Notification) {
        transactions.executeWithoutResult {
            jdbc.sql(
                """
                INSERT INTO notifications (id, group_name, title, subject, body, body_format, schedule_kind, run_at, cron,
                                           zone, until, status, next_run_at, last_run_at, created_by, created_at,
                                           updated_by, updated_at)
                VALUES (:id, :group, :title, :subject, :body, :format, :kind, :runAt, :cron,
                        :zone, :until, :status, :nextRunAt, :lastRunAt, :createdBy, :createdAt, :updatedBy, :updatedAt)
                ON CONFLICT (id) DO UPDATE
                SET group_name = EXCLUDED.group_name, title = EXCLUDED.title, subject = EXCLUDED.subject,
                    body = EXCLUDED.body, body_format = EXCLUDED.body_format, schedule_kind = EXCLUDED.schedule_kind,
                    run_at = EXCLUDED.run_at, cron = EXCLUDED.cron, zone = EXCLUDED.zone, until = EXCLUDED.until,
                    status = EXCLUDED.status, next_run_at = EXCLUDED.next_run_at, last_run_at = EXCLUDED.last_run_at,
                    updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
                """,
            )
                .param("id", notification.id)
                .param("group", notification.group)
                .param("title", notification.title)
                .param("subject", notification.subject)
                .param("body", notification.body)
                .param("format", notification.format.name)
                .param("kind", notification.schedule.kind.name)
                .param("runAt", notification.schedule.runAt?.toTimestamptz())
                .param("cron", notification.schedule.cron)
                .param("zone", notification.schedule.zone.id)
                .param("until", notification.schedule.until?.toTimestamptz())
                .param("status", notification.status.name)
                .param("nextRunAt", notification.nextRunAt?.toTimestamptz())
                .param("lastRunAt", notification.lastRunAt?.toTimestamptz())
                .param("createdBy", notification.createdBy)
                .param("createdAt", notification.createdAt.toTimestamptz())
                .param("updatedBy", notification.updatedBy)
                .param("updatedAt", notification.updatedAt.toTimestamptz())
                .update()
            jdbc.sql("DELETE FROM deliveries WHERE notification_id = :id").param("id", notification.id).update()
            notification.deliveries.forEachIndexed { position, delivery ->
                jdbc.sql(
                    """
                    INSERT INTO deliveries (id, notification_id, position, plugin, label, enabled, target)
                    VALUES (:id, :notificationId, :position, :plugin, :label, :enabled, CAST(:target AS jsonb))
                    """,
                )
                    .param("id", delivery.id)
                    .param("notificationId", notification.id)
                    .param("position", position)
                    .param("plugin", delivery.plugin)
                    .param("label", delivery.label)
                    .param("enabled", delivery.enabled)
                    .param("target", json.writeValueAsString(delivery.target))
                    .update()
            }
        }
    }

    override fun delete(id: UUID): Boolean =
        jdbc.sql("DELETE FROM notifications WHERE id = :id").param("id", id).update() > 0

    override fun due(now: Instant, limit: Int): List<Notification> = load(
        "WHERE status = 'ACTIVE' AND next_run_at <= :now ORDER BY next_run_at LIMIT :limit",
        mapOf("now" to now.toTimestamptz(), "limit" to limit),
        ordered = true,
    )

    override fun advance(id: UUID, expected: Instant, nextRunAt: Instant?, status: NotificationStatus, lastRunAt: Instant): Boolean =
        jdbc.sql(
            """
            UPDATE notifications SET next_run_at = :nextRunAt, status = :status, last_run_at = :lastRunAt
            WHERE id = :id AND status = 'ACTIVE' AND next_run_at = :expected
            """,
        )
            .param("id", id)
            .param("expected", expected.toTimestamptz())
            .param("nextRunAt", nextRunAt?.toTimestamptz())
            .param("status", status.name)
            .param("lastRunAt", lastRunAt.toTimestamptz())
            .update() > 0

    override fun groups(): Set<String> =
        jdbc.sql("SELECT DISTINCT group_name FROM notifications").query { rs, _ -> rs.getString(1) }.list().toSortedSet()

    private fun load(clause: String, params: Map<String, Any?>, ordered: Boolean = false): List<Notification> {
        val order = if (ordered) "" else " ORDER BY created_at, id"
        val rows = jdbc.sql("SELECT * FROM notifications $clause$order").params(params).query { rs, _ -> rs.toRow() }.list()
        if (rows.isEmpty()) return emptyList()
        val deliveries = jdbc.sql("SELECT * FROM deliveries WHERE notification_id IN (:ids) ORDER BY position")
            .param("ids", rows.map { it.id })
            .query { rs, _ -> rs.getObject("notification_id", UUID::class.java) to rs.toDelivery() }
            .list()
            .groupBy({ it.first }, { it.second })
        return rows.map { it.copy(deliveries = deliveries[it.id].orEmpty()) }
    }

    private fun ResultSet.toRow() = Notification(
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
        ),
        status = NotificationStatus.valueOf(getString("status")),
        deliveries = emptyList(),
        nextRunAt = getInstant("next_run_at"),
        lastRunAt = getInstant("last_run_at"),
        createdBy = getString("created_by"),
        createdAt = checkNotNull(getInstant("created_at")),
        updatedBy = getString("updated_by"),
        updatedAt = checkNotNull(getInstant("updated_at")),
    )

    private fun ResultSet.toDelivery() = NotificationDelivery(
        id = getObject("id", UUID::class.java),
        plugin = getString("plugin"),
        target = json.readValue(getString("target"), TARGET),
        label = getString("label"),
        enabled = getBoolean("enabled"),
    )

    private companion object {
        val TARGET = object : TypeReference<Map<String, String>>() {}
    }
}
