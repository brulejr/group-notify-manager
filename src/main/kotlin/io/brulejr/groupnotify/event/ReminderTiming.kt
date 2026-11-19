/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

/** Whether a reminder goes out before an occurrence starts, as it starts, or after it. */
enum class ReminderTiming(val label: String) {
    BEFORE("before"),
    AT_START("at the start"),
    AFTER("after"),
}
