/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

/** How the console draws a [PluginField], and what it checks before saving. */
enum class FieldType {
    TEXT,
    TEXTAREA,

    /** One address or several, separated by commas. */
    EMAIL,
    URL,

    /** E.164, e.g. `+15551234567`; several separated by commas. */
    PHONE,
    NUMBER,

    /** One of [PluginField.options]. */
    SELECT,

    /** Stored as `true` or `false`. */
    CHECKBOX,
}
