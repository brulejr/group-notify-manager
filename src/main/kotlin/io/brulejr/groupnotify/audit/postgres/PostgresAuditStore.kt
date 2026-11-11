/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit.postgres

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditPort
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.DispatchTrigger
import io.brulejr.groupnotify.platform.getInstant
import io.brulejr.groupnotify.platform.toTimestamptz
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** PostgreSQL-backed [AuditPort]: the `audit_events` table. */
class PostgresAuditStore(private val jdbc: JdbcClient) : AuditPort {

    override fun record(event: AuditEvent): AuditEvent {
        val id = jdbc.sql(
            """
            INSERT INTO audit_events (occurred_at, type, actor, notification_id, notification_title, group_name,
                                      delivery_id, plugin, target, trigger, detail, reference, attempts)
            VALUES (:occurredAt, :type, :actor, :notificationId, :title, :group,
                    :deliveryId, :plugin, :target, :trigger, :detail, :reference, :attempts)
            RETURNING id
            """,
        )
            .param("occurredAt", event.occurredAt.toTimestamptz())
            .param("type", event.type.name)
            .param("actor", event.actor)
            .param("notificationId", event.notificationId)
            .param("title", event.notificationTitle)
            .param("group", event.group)
            .param("deliveryId", event.deliveryId)
            .param("plugin", event.plugin)
            .param("target", event.target)
            .param("trigger", event.trigger?.name)
            .param("detail", event.detail)
            .param("reference", event.reference)
            .param("attempts", event.attempts)
            .query(Long::class.java)
            .single()
        return event.copy(id = id)
    }

    override fun find(query: AuditQuery): List<AuditEvent> {
        val (where, params) = where(query, paging = true)
        return jdbc.sql("SELECT * FROM audit_events $where ORDER BY id DESC LIMIT :limit")
            .params(params + ("limit" to query.limit))
            .query { rs, _ -> rs.toEvent() }
            .list()
    }

    override fun count(query: AuditQuery): Map<AuditEventType, Long> {
        val (where, params) = where(query, paging = false)
        return jdbc.sql("SELECT type, count(*) AS n FROM audit_events $where GROUP BY type")
            .params(params)
            .query { rs, _ -> AuditEventType.valueOf(rs.getString("type")) to rs.getLong("n") }
            .list()
            .toMap()
    }

    override fun purge(cutoff: Instant): Int =
        jdbc.sql("DELETE FROM audit_events WHERE occurred_at < :cutoff").param("cutoff", cutoff.toTimestamptz()).update()

    private fun where(query: AuditQuery, paging: Boolean): Pair<String, Map<String, Any?>> {
        val clauses = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()
        query.groups?.let {
            if (it.isEmpty()) clauses += "false" else { clauses += "group_name IN (:groups)"; params["groups"] = it.toList() }
        }
        query.notificationId?.let { clauses += "notification_id = :notificationId"; params["notificationId"] = it }
        query.types?.let {
            if (it.isEmpty()) clauses += "false" else { clauses += "type IN (:types)"; params["types"] = it.map { t -> t.name } }
        }
        query.since?.let { clauses += "occurred_at >= :since"; params["since"] = it.toTimestamptz() }
        if (paging) query.beforeId?.let { clauses += "id < :beforeId"; params["beforeId"] = it }
        return (if (clauses.isEmpty()) "" else "WHERE " + clauses.joinToString(" AND ")) to params
    }

    private fun ResultSet.toEvent() = AuditEvent(
        id = getLong("id"),
        occurredAt = checkNotNull(getInstant("occurred_at")),
        type = AuditEventType.valueOf(getString("type")),
        actor = getString("actor"),
        notificationId = getObject("notification_id", UUID::class.java),
        notificationTitle = getString("notification_title"),
        group = getString("group_name"),
        deliveryId = getObject("delivery_id", UUID::class.java),
        plugin = getString("plugin"),
        target = getString("target"),
        trigger = getString("trigger")?.let(DispatchTrigger::valueOf),
        detail = getString("detail"),
        reference = getString("reference"),
        attempts = getObject("attempts") as Int?,
    )
}
