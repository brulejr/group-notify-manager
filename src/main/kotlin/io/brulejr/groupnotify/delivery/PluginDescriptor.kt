/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.PluginField

/** A plugin as the console and API describe it. */
data class PluginDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val fields: List<PluginField>,
    val ready: Boolean,
    val reason: String,
    val source: String,
    /** The keys under `notify.delivery.settings.<id>` that are set, never their values. */
    val configuredSettings: List<String>,
)
