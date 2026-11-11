/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.api

import io.brulejr.groupnotify.notification.NotificationProblem

/** Returned with 400 when a notification is refused. */
data class NotificationErrors(val message: String, val problems: List<NotificationProblem>) {
    companion object {
        fun of(problems: List<NotificationProblem>) =
            NotificationErrors(problems.joinToString("; ") { "${it.field}: ${it.reason}" }, problems)
    }
}
