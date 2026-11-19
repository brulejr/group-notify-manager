/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.api

import io.brulejr.groupnotify.event.Event
import io.brulejr.groupnotify.event.EventProperty
import io.brulejr.groupnotify.event.OccurrenceNote
import io.brulejr.groupnotify.event.ReminderTiming
import io.brulejr.groupnotify.event.ReminderUnit
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.api.NotificationView
import java.time.Instant
import java.util.UUID

/** An event as the REST API returns it. No secret appears: those stay on the instances. */
data class EventView(
    val id: UUID,
    val group: String,
    val title: String,
    val subject: String,
    val body: String,
    val format: BodyFormat,
    val schedule: NotificationView.ScheduleView,
    val properties: List<EventProperty>,
    val reminders: List<ReminderView>,
    val deliveries: List<NotificationView.DeliveryView>,
    val notes: Map<Instant, OccurrenceNote>,
    val status: NotificationStatus,
    val next: NextView?,
    val lastSentAt: Instant?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
) {
    /** @property describe e.g. `1 day before`. */
    data class ReminderView(
        val id: UUID,
        val amount: Int,
        val unit: ReminderUnit,
        val timing: ReminderTiming,
        val describe: String,
        val subject: String,
        val body: String,
        val enabled: Boolean,
    )

    /** The reminder due next: when, for which occurrence, which reminder. */
    data class NextView(val sendAt: Instant, val occurrence: Instant, val reminder: UUID, val describe: String?)

    companion object {
        fun of(e: Event, instances: InstanceService) = EventView(
            id = e.id,
            group = e.group,
            title = e.title,
            subject = e.subject,
            body = e.body,
            format = e.format,
            schedule = NotificationView.schedule(e.schedule),
            properties = e.properties,
            reminders = e.reminders.map { ReminderView(it.id, it.amount, it.unit, it.timing, it.describe(), it.subject, it.body, it.enabled) },
            deliveries = NotificationView.deliveries(e.deliveries, instances),
            notes = e.notes,
            status = e.status,
            next = e.next?.let { NextView(it.sendAt, it.occurrence, it.reminder, e.reminder(it.reminder)?.describe()) },
            lastSentAt = e.lastSentAt,
            createdBy = e.createdBy,
            createdAt = e.createdAt,
            updatedBy = e.updatedBy,
            updatedAt = e.updatedAt,
        )
    }
}
