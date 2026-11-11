/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import java.util.UUID

/** A delivery as lists and the detail page show it. */
data class DeliveryBadge(
    val id: UUID,
    val plugin: String,
    val pluginName: String,
    val icon: String,
    val label: String,
    val summary: String,
    val enabled: Boolean,
    val ready: Boolean,
    val reason: String,
)
