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
 * Which events to list, newest first.
 *
 * @property groups only these groups' events; null for every group. An empty set
 *   matches nothing.
 * @property beforeId only events older than this one, for the next page.
 */
data class AuditQuery(
    val groups: Set<String>? = null,
    val notificationId: UUID? = null,
    val types: Set<AuditEventType>? = null,
    val since: Instant? = null,
    val beforeId: Long? = null,
    val limit: Int = 50,
) {
    init {
        require(limit in 1..1000) { "limit must be between 1 and 1000" }
    }
}
