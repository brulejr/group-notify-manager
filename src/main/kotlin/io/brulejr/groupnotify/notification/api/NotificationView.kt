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
import io.brulejr.groupnotify.notification.NotificationDelivery
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.Schedule
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
        /** A schedule as the API shows it, a pattern in its canonical words and in English. */
        fun schedule(s: Schedule) = ScheduleView(
            s.kind, s.zone.id, s.runAt?.atZone(s.zone)?.toLocalDateTime(), s.cron, s.until, s.pattern, s.parsedPattern?.describe(),
        )

        /** Deliveries by their instances, with their overrides; never a secret. */
        fun deliveries(list: List<NotificationDelivery>, instances: InstanceService) = list.map { d ->
            val instance = instances.get(d.instance)
            DeliveryView(d.id, d.instance, instance?.name, instance?.plugin, d.enabled, instance?.let(instances::summary) ?: "deleted instance", d.overrides)
        }

        fun of(n: Notification, instances: InstanceService) = NotificationView(
            id = n.id,
            group = n.group,
            title = n.title,
            subject = n.subject,
            body = n.body,
            format = n.format,
            schedule = schedule(n.schedule),
            status = n.status,
            nextRunAt = n.nextRunAt,
            lastRunAt = n.lastRunAt,
            deliveries = deliveries(n.deliveries, instances),
            createdBy = n.createdBy,
            createdAt = n.createdAt,
            updatedBy = n.updatedBy,
            updatedAt = n.updatedAt,
        )
    }
}
