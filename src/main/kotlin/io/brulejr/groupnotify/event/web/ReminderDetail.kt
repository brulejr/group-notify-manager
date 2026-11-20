/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.web

import io.brulejr.groupnotify.event.Reminder
import io.brulejr.groupnotify.notification.web.DeliveryBadge

/** A reminder on its event's page, with where it goes. */
data class ReminderDetail(val reminder: Reminder, val deliveries: MutableList<DeliveryBadge>)
