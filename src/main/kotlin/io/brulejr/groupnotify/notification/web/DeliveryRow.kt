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
import io.brulejr.groupnotify.delivery.web.FieldInput

/**
 * One delivery in the form: the instance it sends through, and an input for each field
 * of the plugin a notification may override, showing the value it will use.
 *
 * @property instance the instance's id as text, as the form sends it back.
 * @property name the instance's name, or why it cannot be shown.
 * @property summary where the instance sends.
 */
data class DeliveryRow(
    val index: Int,
    val id: String,
    val instance: String,
    val name: String,
    val plugin: PluginDescriptor?,
    val summary: String,
    val enabled: Boolean,
    val inputs: MutableList<FieldInput>,
    val error: String?,
)
