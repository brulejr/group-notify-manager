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
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.ScheduleInput

/**
 * An event as submitted, before it is checked: from the console's form or the REST API.
 *
 * @property deliveries as a notification's: one of the group's instances each, with overrides.
 */
data class EventDraft(
    val group: String,
    val title: String,
    val subject: String,
    val body: String,
    val format: BodyFormat,
    val schedule: ScheduleInput,
    val properties: List<EventProperty> = emptyList(),
    val reminders: List<EventDraft.Reminder> = emptyList(),
    val deliveries: List<NotificationDraft.Delivery> = emptyList(),
    val paused: Boolean = false,
) {
    /**
     * A reminder as submitted; [amount] is text, so one that does not parse is reported
     * against its input.
     *
     * @property id the stored reminder this replaces; null for a new one.
     */
    data class Reminder(
        val id: String? = null,
        val amount: String = "",
        val unit: ReminderUnit = ReminderUnit.DAYS,
        val timing: ReminderTiming = ReminderTiming.BEFORE,
        val subject: String = "",
        val body: String = "",
        val enabled: Boolean = true,
    )
}
