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

/**
 * A delivery as lists and the detail page show it.
 *
 * @property name the instance's name, e.g. `Club announcements`.
 * @property overridden the labels of the fields this notification sets for itself.
 */
data class DeliveryBadge(
    val id: UUID,
    val name: String,
    val pluginName: String,
    val icon: String,
    val summary: String,
    val overridden: MutableList<String>,
    val enabled: Boolean,
    val ready: Boolean,
    val reason: String,
)
