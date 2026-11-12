/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery.web

import io.brulejr.groupnotify.plugin.PluginField

/**
 * One input for a plugin field, on an instance's form or as a notification's override,
 * drawn by `ui/plugins/fields :: field`.
 *
 * @property saved a secret is stored for it: the input is empty, and left so keeps it.
 * @property note shown under the input, e.g. that a notification may override it.
 */
data class FieldInput(
    val field: PluginField,
    val name: String,
    val inputId: String,
    val value: String,
    val saved: Boolean,
    val error: String?,
    val note: String? = null,
)
