/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

import java.time.Instant
import java.util.UUID

/**
 * One entry in a notification's history. Kept after the notification is deleted, so it
 * names the notification as it was then rather than referring to it.
 *
 * @property id assigned when recorded; newer events have larger ids.
 * @property actor the user who did it, or `scheduler`.
 * @property target the plugin's summary of where a delivery went, never a secret.
 * @property reference the receiving service's own id for a delivery, when it gives one.
 * @property attempts how many tries a delivery took.
 */
data class AuditEvent(
    val id: Long? = null,
    val occurredAt: Instant,
    val type: AuditEventType,
    val actor: String,
    val notificationId: UUID?,
    val notificationTitle: String,
    val group: String,
    val deliveryId: UUID? = null,
    val plugin: String? = null,
    val target: String? = null,
    val trigger: DispatchTrigger? = null,
    val detail: String = "",
    val reference: String? = null,
    val attempts: Int? = null,
) {
    companion object {
        const val SCHEDULER = "scheduler"
    }
}
