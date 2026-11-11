/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import java.time.Instant
import java.util.UUID

/**
 * A message a group sends, once or on a schedule, through one or more deliveries.
 *
 * @property group the OIDC group it belongs to: its members see it, and only they and
 *   admins.
 * @property title what it is called in the console. The subject is what recipients see.
 * @property body Markdown or HTML, as [format] says; it may hold placeholders.
 * @property nextRunAt when it is next sent; null while paused or once completed.
 * @property lastRunAt when it was last sent on schedule.
 */
data class Notification(
    val id: UUID,
    val group: String,
    val title: String,
    val subject: String,
    val body: String,
    val format: BodyFormat,
    val schedule: Schedule,
    val status: NotificationStatus,
    val deliveries: List<NotificationDelivery>,
    val nextRunAt: Instant?,
    val lastRunAt: Instant?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
