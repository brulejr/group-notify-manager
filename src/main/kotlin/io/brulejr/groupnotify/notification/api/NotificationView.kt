/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.api

import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.ScheduleKind
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/**
 * A notification as the REST API returns it: secrets masked, times both as instants and
 * as local times in the schedule's zone.
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
    data class ScheduleView(val kind: ScheduleKind, val zone: String, val at: LocalDateTime?, val cron: String?, val until: Instant?)

    data class DeliveryView(
        val id: UUID,
        val plugin: String,
        val label: String,
        val enabled: Boolean,
        val summary: String,
        val target: Map<String, String>,
    )

    companion object {
        fun of(n: Notification, deliveries: DeliveryService) = NotificationView(
            id = n.id,
            group = n.group,
            title = n.title,
            subject = n.subject,
            body = n.body,
            format = n.format,
            schedule = ScheduleView(
                n.schedule.kind, n.schedule.zone.id,
                n.schedule.runAt?.atZone(n.schedule.zone)?.toLocalDateTime(), n.schedule.cron, n.schedule.until,
            ),
            status = n.status,
            nextRunAt = n.nextRunAt,
            lastRunAt = n.lastRunAt,
            deliveries = n.deliveries.map {
                DeliveryView(it.id, it.plugin, it.label, it.enabled, deliveries.summarize(it.plugin, it.target), deliveries.mask(it.plugin, it.target))
            },
            createdBy = n.createdBy,
            createdAt = n.createdAt,
            updatedBy = n.updatedBy,
            updatedAt = n.updatedAt,
        )
    }
}
