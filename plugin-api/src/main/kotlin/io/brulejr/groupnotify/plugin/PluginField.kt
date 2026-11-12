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
 * One input on a plugin instance's form.
 *
 * Every field is set when an admin provisions an instance of the plugin for a group.
 * An [overridable] one is only a default there: a notification using the instance may
 * change it for itself. Credentials and where the plugin sends must never be
 * overridable, so that a notification can only ever send where its group's instances do.
 *
 * @property key how the value is stored in the [DeliveryTarget], e.g. `webhookUrl`.
 * @property secret the value is never shown again once saved, not in the console, the
 *   REST API or the audit trail, and is encrypted at rest. Leaving it blank when
 *   editing keeps the stored value. A secret is never overridable.
 * @property options the choices of a [FieldType.SELECT].
 * @property defaultValue what a new instance starts with.
 * @property overridable a notification may set its own value, e.g. a post's hashtags.
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
    val overridable: Boolean = false,
) {
    init {
        require(KEY.matches(key)) { "plugin field key '$key' must be a letter followed by letters and digits" }
        require(type != FieldType.SELECT || options.isNotEmpty()) { "select field '$key' needs options" }
        require(!(secret && overridable)) { "secret field '$key' cannot be overridable" }
    }

    private companion object {
        val KEY = Regex("[A-Za-z][A-Za-z0-9]*")
    }
}
