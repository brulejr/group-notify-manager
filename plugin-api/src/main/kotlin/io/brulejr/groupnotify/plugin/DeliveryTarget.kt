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
 * Where one delivery goes and how: an instance's values for the plugin's
 * [DeliveryPlugin.fields] with the notification's overrides applied, keyed by
 * [PluginField.key], secrets in the clear. A field left blank is absent.
 */
data class DeliveryTarget(val values: Map<String, String>) {

    /** The value of [key], or null when blank or absent. */
    operator fun get(key: String): String? = values[key]?.takeIf { it.isNotBlank() }

    /** The value of [key]; for required fields, which the application has already checked. */
    fun require(key: String): String = checkNotNull(get(key)) { "target has no $key" }

    fun flag(key: String): Boolean = get(key)?.toBooleanStrictOrNull() ?: false

    /** A comma- or newline-separated value, split and trimmed. */
    fun list(key: String): List<String> = get(key)?.split(',', '\n', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
