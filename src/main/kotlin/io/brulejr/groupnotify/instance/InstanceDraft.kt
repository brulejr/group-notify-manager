/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

/**
 * An instance as submitted, before it is checked. When editing, [group] and [plugin] must
 * be the instance's own, and a secret left blank or sent back masked keeps its value.
 */
data class InstanceDraft(
    val group: String,
    val plugin: String,
    val name: String,
    val config: Map<String, String> = emptyMap(),
)
