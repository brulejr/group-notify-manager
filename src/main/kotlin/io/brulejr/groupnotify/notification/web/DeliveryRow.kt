/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import io.brulejr.groupnotify.delivery.PluginDescriptor

/** One delivery in the form: which plugin, and an input for each of its fields. */
data class DeliveryRow(
    val index: Int,
    val id: String,
    val plugin: PluginDescriptor,
    val label: String,
    val enabled: Boolean,
    val inputs: MutableList<FieldInput>,
    val error: String?,
)
