/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.web

import io.brulejr.groupnotify.instance.PluginInstance

/**
 * An instance as the Delivery plugins page lists it.
 *
 * @property usedBy the titles of the notifications that send through it.
 */
data class InstanceRow(
    val instance: PluginInstance,
    val groupName: String,
    val pluginName: String,
    val icon: String,
    val ready: Boolean,
    val reason: String,
    val summary: String,
    val usedBy: MutableList<String>,
)
