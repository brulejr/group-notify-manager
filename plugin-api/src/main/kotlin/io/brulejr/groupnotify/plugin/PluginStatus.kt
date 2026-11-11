/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

/**
 * Whether a plugin can deliver. A plugin that is not ready still loads and can be chosen
 * for a delivery; its deliveries fail, recorded with [reason], until it is configured.
 */
data class PluginStatus(val ready: Boolean, val reason: String = "") {
    companion object {
        fun ready() = PluginStatus(true)

        fun notReady(reason: String) = PluginStatus(false, reason)

        /** Not ready because these settings are missing, or ready if none are. */
        fun requireSettings(settings: PluginSettings, prefix: String, vararg keys: String): PluginStatus {
            val missing = settings.missing(*keys)
            return if (missing.isEmpty()) ready() else notReady("missing ${missing.joinToString { "$prefix.$it" }}")
        }
    }
}
