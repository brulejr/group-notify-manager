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
 * One input on a delivery's form.
 *
 * @property key how the value is stored in the [DeliveryTarget], e.g. `webhookUrl`.
 * @property secret the value is never shown again once saved, not in the console, the
 *   REST API or the audit trail. Leaving it blank when editing keeps the stored value.
 * @property options the choices of a [FieldType.SELECT].
 * @property defaultValue what a new delivery starts with.
 */
data class PluginField(
    val key: String,
    val label: String,
    val type: FieldType = FieldType.TEXT,
    val required: Boolean = false,
    val secret: Boolean = false,
    val help: String = "",
    val placeholder: String = "",
    val options: List<FieldOption> = emptyList(),
    val defaultValue: String = "",
) {
    init {
        require(KEY.matches(key)) { "plugin field key '$key' must be a letter followed by letters and digits" }
        require(type != FieldType.SELECT || options.isNotEmpty()) { "select field '$key' needs options" }
    }

    private companion object {
        val KEY = Regex("[A-Za-z][A-Za-z0-9]*")
    }
}
