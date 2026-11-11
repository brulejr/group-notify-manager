/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

/** Whether a notification will be sent when its time comes. */
enum class NotificationStatus(val label: String, val level: String) {
    ACTIVE("Active", "success"),

    /** Held: nothing is sent until it is resumed. */
    PAUSED("Paused", "warning"),

    /** Nothing left to send: a one-off that has gone, or a recurring one past its end. */
    COMPLETED("Completed", "secondary"),
}
