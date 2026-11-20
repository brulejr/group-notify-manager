/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.api

import io.brulejr.groupnotify.event.EventDraft
import io.brulejr.groupnotify.event.EventProperty
import io.brulejr.groupnotify.event.ReminderTiming
import io.brulejr.groupnotify.event.ReminderUnit
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.api.NotificationRequest

/**
 * An event as the REST API takes it. The schedule is a notification's; properties are
 * `{"name": "location", "value": "Town hall"}`; reminders are
 * `{"amount": 1, "unit": "DAYS", "timing": "BEFORE", "deliveries": [...]}`, with an
 * optional own `subject` and Markdown `body`, and each its own deliveries, as a
 * notification's are.
 */
data class EventRequest(
    val group: String = "",
    val title: String = "",
    val subject: String = "",
    val body: String = "",
    val format: BodyFormat = BodyFormat.MARKDOWN,
    val schedule: NotificationRequest.ScheduleRequest = NotificationRequest.ScheduleRequest(),
    val properties: List<EventProperty> = emptyList(),
    val reminders: List<ReminderRequest> = emptyList(),
    val paused: Boolean = false,
) {
    /** @property id the stored reminder this replaces, so it keeps its id. */
    data class ReminderRequest(
        val id: String? = null,
        val amount: Int = 0,
        val unit: ReminderUnit = ReminderUnit.DAYS,
        val timing: ReminderTiming = ReminderTiming.BEFORE,
        val subject: String = "",
        val body: String = "",
        val enabled: Boolean = true,
        val deliveries: List<NotificationRequest.DeliveryRequest> = emptyList(),
    )

    fun toDraft() = EventDraft(
        group = group,
        title = title,
        subject = subject,
        body = body,
        format = format,
        schedule = schedule.toInput(),
        properties = properties,
        reminders = reminders.map { r ->
            EventDraft.Reminder(
                r.id, r.amount.toString(), r.unit, r.timing, r.subject, r.body, r.enabled,
                r.deliveries.map { NotificationDraft.Delivery(it.id, it.instance, it.overrides, it.enabled) },
            )
        },
        paused = paused,
    )
}
