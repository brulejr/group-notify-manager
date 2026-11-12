/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.api

import io.brulejr.groupnotify.instance.InstanceDraft

/**
 * An instance as the REST API takes it. When updating, `group` and `plugin` must be the
 * instance's own, and a secret left out or sent back as `********` keeps its value.
 */
data class InstanceRequest(
    val group: String = "",
    val plugin: String = "",
    val name: String = "",
    val config: Map<String, String> = emptyMap(),
) {
    fun toDraft() = InstanceDraft(group, plugin, name, config)
}
