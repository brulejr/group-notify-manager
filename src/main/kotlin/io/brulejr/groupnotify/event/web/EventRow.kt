/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.web

import io.brulejr.groupnotify.event.Event
import io.brulejr.groupnotify.notification.web.DeliveryBadge

/**
 * An event as its list shows it.
 *
 * @property nextOccurrence when it next happens, whatever its reminders.
 * @property nextReminder e.g. `1 hour before`, the reminder due next.
 */
data class EventRow(
    val event: Event,
    val groupName: String,
    val canEdit: Boolean,
    val deliveries: MutableList<DeliveryBadge>,
    val nextOccurrence: java.time.Instant?,
    val nextReminder: String?,
)
