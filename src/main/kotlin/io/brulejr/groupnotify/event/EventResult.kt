/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.notification.NotificationProblem

/** Outcome of changing an event. */
sealed interface EventResult {

    data class Saved(val event: Event) : EventResult

    /** Refused, with every problem found; nothing was written. */
    data class Invalid(val problems: List<NotificationProblem>) : EventResult

    /** No such event, or not one the user may see: the two are not told apart. */
    data object NotFound : EventResult
}
