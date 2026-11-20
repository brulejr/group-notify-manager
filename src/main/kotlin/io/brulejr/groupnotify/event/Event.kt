/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationDelivery
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.Schedule
import java.time.Instant
import java.util.UUID

/**
 * Something a group holds on a date, once or on a schedule, with reminders sent
 * relative to each occurrence: a club meeting, a net, a field day.
 *
 * Its [subject] and [body] may use the standard placeholders, its own, such as
 * `{{event.date}}` and `{{event.when}}`, and one per property, `{{event.<name>}}`. A
 * reminder sends them unless it has its own words.
 *
 * Like a notification, it belongs to one group, whose members see and manage it, and
 * goes out through that group's delivery instances, which each reminder chooses for
 * itself.
 *
 * @property schedule when it happens: each run of it is an occurrence.
 * @property notes an occurrence's own note, keyed by its start, e.g. "This week: guest
 *   speaker", in Markdown or HTML; added after the body unless the body places it with
 *   `{{occurrence.note}}`.
 * @property next the reminder due next; null while paused or once none is left.
 * @property lastSentAt when a reminder was last sent on schedule.
 */
data class Event(
    val id: UUID,
    val group: String,
    val title: String,
    val subject: String,
    val body: String,
    val format: BodyFormat,
    val schedule: Schedule,
    val properties: List<EventProperty>,
    val reminders: List<Reminder>,
    val notes: Map<Instant, OccurrenceNote>,
    val status: NotificationStatus,
    val next: PendingReminder?,
    val lastSentAt: Instant?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
) {
    val nextRunAt: Instant? get() = next?.sendAt

    fun reminder(id: UUID): Reminder? = reminders.firstOrNull { it.id == id }

    /** Every reminder's deliveries, for what the event uses. */
    val allDeliveries: List<NotificationDelivery> get() = reminders.flatMap { it.deliveries }
}
