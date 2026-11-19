/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.notification.NotificationStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory [EventPort], for running without a database. Nothing survives a restart. */
class InMemoryEventStore : EventPort {

    private val byId = ConcurrentHashMap<UUID, Event>()

    override fun find(id: UUID): Event? = byId[id]

    override fun list(groups: Set<String>?): List<Event> =
        byId.values.filter { groups == null || it.group in groups }.sortedBy { it.createdAt }

    override fun save(event: Event) {
        byId[event.id] = event
    }

    override fun delete(id: UUID): Boolean = byId.remove(id) != null

    override fun due(now: Instant, limit: Int): List<Event> = byId.values
        .filter { it.status == NotificationStatus.ACTIVE && it.nextRunAt != null && it.nextRunAt!! <= now }
        .sortedBy { it.nextRunAt }
        .take(limit)

    override fun advance(id: UUID, expected: Instant, next: PendingReminder?, status: NotificationStatus, sentAt: Instant): Boolean {
        var moved = false
        byId.computeIfPresent(id) { _, current ->
            if (current.status == NotificationStatus.ACTIVE && current.nextRunAt == expected) {
                moved = true
                current.copy(next = next, status = status, lastSentAt = sentAt)
            } else {
                current
            }
        }
        return moved
    }

    override fun groups(): Set<String> = byId.values.map { it.group }.toSortedSet()
}
