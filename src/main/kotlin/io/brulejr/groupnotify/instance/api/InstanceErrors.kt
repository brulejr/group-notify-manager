/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.api

import io.brulejr.groupnotify.instance.InstanceProblem

/**
 * Returned with 400 when an instance is refused, or 409 when it is still in use.
 *
 * @property notifications for 409, the titles of the notifications that use it.
 */
data class InstanceErrors(
    val message: String,
    val problems: List<InstanceProblem> = emptyList(),
    val notifications: List<String> = emptyList(),
) {
    companion object {
        fun of(problems: List<InstanceProblem>) = InstanceErrors(problems.joinToString("; ") { "${it.field}: ${it.reason}" }, problems)

        fun inUse(notifications: List<String>) =
            InstanceErrors("still used by ${notifications.size} notification(s): ${notifications.joinToString()}", notifications = notifications)
    }
}
