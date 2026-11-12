/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory [InstancePort], for running without a database. Nothing survives a restart. */
class InMemoryInstanceStore : InstancePort {

    private val byId = ConcurrentHashMap<UUID, PluginInstance>()

    override fun find(id: UUID): PluginInstance? = byId[id]

    override fun list(groups: Set<String>?): List<PluginInstance> = byId.values
        .filter { groups == null || it.group in groups }
        .sortedWith(compareBy({ it.group }, { it.name.lowercase() }))

    override fun save(instance: PluginInstance) {
        byId[instance.id] = instance
    }

    override fun delete(id: UUID): Boolean = byId.remove(id) != null
}
