/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

/** Outcome of changing a notification. */
sealed interface NotificationResult {

    data class Saved(val notification: Notification) : NotificationResult

    /** Refused, with every problem found; nothing was written. */
    data class Invalid(val problems: List<NotificationProblem>) : NotificationResult

    /** No such notification, or not one the user may see: the two are not told apart. */
    data object NotFound : NotificationResult

    /** The user may see it but not change it. */
    data object Forbidden : NotificationResult
}
