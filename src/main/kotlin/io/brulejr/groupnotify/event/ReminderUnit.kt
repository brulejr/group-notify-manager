/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

/** The unit a reminder's offset is counted in. Days and weeks are counted on the local calendar. */
enum class ReminderUnit(val singular: String, val plural: String) {
    MINUTES("minute", "minutes"),
    HOURS("hour", "hours"),
    DAYS("day", "days"),
    WEEKS("week", "weeks"),
    ;

    fun words(amount: Int) = "$amount ${if (amount == 1) singular else plural}"
}
