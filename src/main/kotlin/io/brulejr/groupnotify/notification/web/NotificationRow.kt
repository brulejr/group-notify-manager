/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import io.brulejr.groupnotify.notification.Notification

/** A notification in the list, with what the row needs beyond it. */
data class NotificationRow(
    val notification: Notification,
    val groupName: String,
    val canEdit: Boolean,
    val deliveries: MutableList<DeliveryBadge>,
)
