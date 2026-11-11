/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

enum class ScheduleKind(val label: String) {
    /** Sent once, at a set time. */
    ONCE("One-off"),

    /** Sent every time a cron expression matches, until an optional end date. */
    RECURRING("Recurring"),
}
