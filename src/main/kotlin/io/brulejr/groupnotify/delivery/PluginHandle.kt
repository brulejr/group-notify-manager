/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.PluginStatus

/**
 * A loaded plugin, and what became of starting it.
 *
 * @property source the jar it came from, or `built in`.
 * @property startError why [DeliveryPlugin.start] threw, if it did; the plugin then
 *   stays loaded but not ready, so existing deliveries fail visibly rather than vanish.
 */
data class PluginHandle(val plugin: DeliveryPlugin, val source: String, val startError: String? = null) {

    val id get() = plugin.id

    fun status(): PluginStatus = startError?.let { PluginStatus.notReady("failed to start: $it") }
        ?: runCatching { plugin.status() }.getOrElse { PluginStatus.notReady("status check failed: ${it.message}") }
}
