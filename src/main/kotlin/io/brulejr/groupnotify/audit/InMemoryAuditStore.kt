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
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [AuditPort], for running without a database: the tests select it with
 * `notify.audit.store=memory`. Keeps at most [capacity] events, dropping the oldest.
 */
class InMemoryAuditStore(private val capacity: Int = 10_000) : AuditPort {

    private val ids = AtomicLong()
    private val events = ArrayDeque<AuditEvent>()

    @Synchronized
    override fun record(event: AuditEvent): AuditEvent {
        val stored = event.copy(id = ids.incrementAndGet())
        events.addFirst(stored)
        while (events.size > capacity) events.removeLast()
        return stored
    }

    @Synchronized
    override fun find(query: AuditQuery): List<AuditEvent> =
        events.asSequence().filter { matches(it, query) && (query.beforeId == null || it.id!! < query.beforeId) }.take(query.limit).toList()

    @Synchronized
    override fun count(query: AuditQuery): Map<AuditEventType, Long> =
        events.filter { matches(it, query) }.groupingBy { it.type }.eachCount().mapValues { it.value.toLong() }

    @Synchronized
    override fun purge(cutoff: Instant): Int {
        val before = events.size
        events.removeAll { it.occurredAt < cutoff }
        return before - events.size
    }

    private fun matches(event: AuditEvent, query: AuditQuery) =
        (query.groups == null || event.group in query.groups) &&
            (query.notificationId == null || event.notificationId == query.notificationId) &&
            (query.types == null || event.type in query.types) &&
            (query.since == null || event.occurredAt >= query.since)
}
