/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

/** Narrows a list of notifications. Every part is optional. */
data class NotificationFilter(
    val q: String? = null,
    val group: String? = null,
    val status: NotificationStatus? = null,
)
