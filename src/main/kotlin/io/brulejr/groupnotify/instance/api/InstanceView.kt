/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.api

import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.PluginInstance
import java.time.Instant
import java.util.UUID

/**
 * An instance as the REST API returns it: secrets masked as `********`, and which of its
 * fields a notification may override.
 */
data class InstanceView(
    val id: UUID,
    val group: String,
    val plugin: String,
    val name: String,
    val summary: String,
    val config: Map<String, String>,
    val overridable: List<String>,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
) {
    companion object {
        fun of(instance: PluginInstance, instances: InstanceService, overridable: Set<String>) = InstanceView(
            id = instance.id,
            group = instance.group,
            plugin = instance.plugin,
            name = instance.name,
            summary = instances.summary(instance),
            config = instances.masked(instance),
            overridable = overridable.sorted(),
            createdBy = instance.createdBy,
            createdAt = instance.createdAt,
            updatedBy = instance.updatedBy,
            updatedAt = instance.updatedAt,
        )
    }
}
