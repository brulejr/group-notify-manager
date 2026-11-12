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

/** Where instances are kept, secrets sealed. Storage only: [InstanceService] checks and authorises. */
interface InstancePort {

    fun find(id: UUID): PluginInstance?

    /** Instances in [groups], or every one when null, by group then name. */
    fun list(groups: Set<String>?): List<PluginInstance>

    /** Stores [instance], replacing any earlier version. */
    fun save(instance: PluginInstance)

    fun delete(id: UUID): Boolean
}
