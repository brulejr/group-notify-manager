/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import java.util.UUID

/**
 * One way a notification goes out: a plugin and the target it is given.
 *
 * @property target the plugin's field values, secrets included; never shown as stored.
 * @property label the author's own name for it, e.g. `Members' channel`.
 * @property enabled a disabled delivery is kept but skipped.
 */
data class NotificationDelivery(
    val id: UUID,
    val plugin: String,
    val target: Map<String, String>,
    val label: String = "",
    val enabled: Boolean = true,
)
