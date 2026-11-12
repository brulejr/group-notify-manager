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
 * A plugin's installation settings, from `notify.delivery.settings.<plugin id>.*`: policy
 * such as timeouts, never credentials, which are an instance's fields. Keyed
 * as written there in kebab case, e.g. `account-sid`. Typically set from the environment,
 * e.g. `NOTIFY_DELIVERY_SETTINGS_DISCORD_TIMEOUT`.
 */
data class PluginSettings(val values: Map<String, String>) {

    /** The value of [key], or null when blank or absent. */
    operator fun get(key: String): String? = values[key]?.takeIf { it.isNotBlank() }

    fun getOrDefault(key: String, default: String): String = get(key) ?: default

    /** The keys among [keys] that have no value: what [DeliveryPlugin.status] should complain of. */
    fun missing(vararg keys: String): List<String> = keys.filter { get(it) == null }

    override fun toString() = "PluginSettings(${values.keys.sorted()})"
}
