/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import io.brulejr.groupnotify.plugin.PluginField

/**
 * One input of a delivery row.
 *
 * @property saved a secret is stored for it: the input is empty, and left so keeps it.
 */
data class FieldInput(
    val field: PluginField,
    val name: String,
    val inputId: String,
    val value: String,
    val saved: Boolean,
    val error: String?,
)
