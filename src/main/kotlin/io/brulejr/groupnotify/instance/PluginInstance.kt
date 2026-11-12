/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import java.time.Instant
import java.util.UUID

/**
 * A delivery plugin provisioned for one group under a name, e.g. the groups.io plugin as
 * "Club announcements" for `notify_club`: everything it needs to send, credentials
 * included, and the defaults a notification may override. Notifications of [group]
 * choose among their group's instances; nothing else can send through it.
 *
 * Only admins provision instances. [group] and [plugin] are fixed once it is created.
 *
 * @property config every one of the plugin's field values, keyed by field key. Secret
 *   values are sealed by [SecretCipher], here and in the store; only
 *   [InstanceService] opens them, and only to deliver.
 */
data class PluginInstance(
    val id: UUID,
    val group: String,
    val plugin: String,
    val name: String,
    val config: Map<String, String>,
    val createdBy: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
