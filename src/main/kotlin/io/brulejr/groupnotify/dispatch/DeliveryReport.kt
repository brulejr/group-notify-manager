/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import io.brulejr.groupnotify.delivery.DeliveryOutcome
import io.brulejr.groupnotify.notification.NotificationDelivery

/** What came of one delivery of a send. */
data class DeliveryReport(
    val delivery: NotificationDelivery,
    val pluginName: String,
    val summary: String,
    val outcome: DeliveryOutcome,
)
