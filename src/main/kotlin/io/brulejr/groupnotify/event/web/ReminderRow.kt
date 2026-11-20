/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.web

import io.brulejr.groupnotify.event.ReminderTiming
import io.brulejr.groupnotify.event.ReminderUnit

/**
 * One reminder's inputs in the form, with any problem against each, and its own
 * deliveries' rows.
 *
 * @property deliveriesError a problem with its deliveries as a whole, e.g. that it has none.
 */
data class ReminderRow(
    val index: Int,
    val id: String,
    val amount: String,
    val unit: ReminderUnit,
    val timing: ReminderTiming,
    val subject: String,
    val body: String,
    val enabled: Boolean,
    val amountError: String?,
    val subjectError: String?,
    val bodyError: String?,
    val deliveries: MutableList<io.brulejr.groupnotify.notification.web.DeliveryRow> = mutableListOf(),
    val deliveriesError: String? = null,
)
