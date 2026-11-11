/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.api

import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.ScheduleKind
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A notification as the REST API takes it. Times are local to the schedule's zone.
 *
 * A delivery's secret fields may be left out, or sent back as `********`, to keep what
 * is stored, provided the delivery carries the `id` it was given.
 */
data class NotificationRequest(
    val group: String = "",
    val title: String = "",
    val subject: String = "",
    val body: String = "",
    val format: BodyFormat = BodyFormat.MARKDOWN,
    val schedule: ScheduleRequest = ScheduleRequest(),
    val deliveries: List<DeliveryRequest> = emptyList(),
    val paused: Boolean = false,
) {
    /** `{"kind": "ONCE", "at": "2026-12-01T18:00"}` or `{"kind": "RECURRING", "cron": "0 18 * * TUE"}`. */
    data class ScheduleRequest(
        val kind: ScheduleKind = ScheduleKind.ONCE,
        val zone: String = "",
        val at: LocalDateTime? = null,
        val cron: String = "",
        val until: LocalDate? = null,
    )

    data class DeliveryRequest(
        val id: String? = null,
        val plugin: String = "",
        val label: String = "",
        val enabled: Boolean = true,
        val target: Map<String, String> = emptyMap(),
    )

    fun toDraft() = NotificationDraft(
        group = group,
        title = title,
        subject = subject,
        body = body,
        format = format,
        kind = schedule.kind,
        zone = schedule.zone,
        runAt = schedule.at,
        cron = schedule.cron,
        until = schedule.until,
        deliveries = deliveries.map { NotificationDraft.Delivery(it.id, it.plugin, it.target, it.label, it.enabled) },
        paused = paused,
    )
}
