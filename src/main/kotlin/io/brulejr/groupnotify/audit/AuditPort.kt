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

/** Where the history is kept. Storage only: [AuditService] decides who may see what. */
interface AuditPort {

    /** Stores [event] and returns it with its id. */
    fun record(event: AuditEvent): AuditEvent

    /** Events matching [query], newest first. */
    fun find(query: AuditQuery): List<AuditEvent>

    /** How many events of each type match [query], ignoring its paging. */
    fun count(query: AuditQuery): Map<AuditEventType, Long>

    /** Deletes events older than [cutoff], returning how many went. */
    fun purge(cutoff: Instant): Int
}
