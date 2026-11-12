/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.api

import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.ScheduleKind
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/**
 * A notification as the REST API returns it: each delivery by its instance, with the
 * notification's overrides, and times both as instants and as local times in the
 * schedule's zone. No secret appears: those stay on the instance.
 */
data class NotificationView(
    val id: UUID,
    val group: String,
    val title: String,
    val subject: String,
    val body: String,
    val format: BodyFormat,
    val schedule: ScheduleView,
    val status: NotificationStatus,
    val nextRunAt: Instant?,
    val lastRunAt: Instant?,
    val deliveries: List<DeliveryView>,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
) {
    /**
     * @property pattern a pattern's canonical words, which always give its start.
     * @property description a pattern in plain English.
     */
    data class ScheduleView(
        val kind: ScheduleKind,
        val zone: String,
        val at: LocalDateTime?,
        val cron: String?,
        val until: Instant?,
        val pattern: String? = null,
        val description: String? = null,
    )

    /**
     * @property instanceName null when the instance has since been deleted.
     * @property overrides only the notification's own values, where they differ.
     */
    data class DeliveryView(
        val id: UUID,
        val instance: UUID,
        val instanceName: String?,
        val plugin: String?,
        val enabled: Boolean,
        val summary: String,
        val overrides: Map<String, String>,
    )

    companion object {
        fun of(n: Notification, instances: InstanceService) = NotificationView(
            id = n.id,
            group = n.group,
            title = n.title,
            subject = n.subject,
            body = n.body,
            format = n.format,
            schedule = ScheduleView(
                n.schedule.kind, n.schedule.zone.id,
                n.schedule.runAt?.atZone(n.schedule.zone)?.toLocalDateTime(), n.schedule.cron, n.schedule.until,
                n.schedule.pattern, n.schedule.parsedPattern?.describe(),
            ),
            status = n.status,
            nextRunAt = n.nextRunAt,
            lastRunAt = n.lastRunAt,
            deliveries = n.deliveries.map { d ->
                val instance = instances.get(d.instance)
                DeliveryView(
                    d.id, d.instance, instance?.name, instance?.plugin, d.enabled,
                    instance?.let(instances::summary) ?: "deleted instance", d.overrides,
                )
            },
            createdBy = n.createdBy,
            createdAt = n.createdAt,
            updatedBy = n.updatedBy,
            updatedAt = n.updatedAt,
        )
    }
}
