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
 * One way a notification goes out: through one of its group's plugin instances.
 *
 * @property instance the [io.brulejr.groupnotify.instance.PluginInstance] it sends through.
 * @property overrides the notification's own values for the plugin's overridable fields,
 *   only where they differ from the instance's; never a secret.
 * @property enabled a disabled delivery is kept but skipped.
 */
data class NotificationDelivery(
    val id: UUID,
    val instance: UUID,
    val overrides: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)
