/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

/** The unit a [SchedulePattern] repeats in: `every N hours`, `days`, `weeks` or `months`. */
enum class Frequency(val singular: String, val plural: String) {
    HOUR("hour", "hours"),
    DAY("day", "days"),
    WEEK("week", "weeks"),
    MONTH("month", "months"),
}
