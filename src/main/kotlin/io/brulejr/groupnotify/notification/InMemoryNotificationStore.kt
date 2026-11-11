/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [NotificationPort], for running without a database: the tests select it with
 * `notify.notifications.store=memory`. Nothing survives a restart.
 */
class InMemoryNotificationStore : NotificationPort {

    private val byId = ConcurrentHashMap<UUID, Notification>()

    override fun find(id: UUID): Notification? = byId[id]

    override fun list(groups: Set<String>?): List<Notification> =
        byId.values.filter { groups == null || it.group in groups }.sortedBy { it.createdAt }

    override fun save(notification: Notification) {
        byId[notification.id] = notification
    }

    override fun delete(id: UUID): Boolean = byId.remove(id) != null

    override fun due(now: Instant, limit: Int): List<Notification> = byId.values
        .filter { it.status == NotificationStatus.ACTIVE && it.nextRunAt != null && it.nextRunAt <= now }
        .sortedBy { it.nextRunAt }
        .take(limit)

    override fun advance(id: UUID, expected: Instant, nextRunAt: Instant?, status: NotificationStatus, lastRunAt: Instant): Boolean {
        var moved = false
        byId.computeIfPresent(id) { _, current ->
            if (current.status == NotificationStatus.ACTIVE && current.nextRunAt == expected) {
                moved = true
                current.copy(nextRunAt = nextRunAt, status = status, lastRunAt = lastRunAt)
            } else {
                current
            }
        }
        return moved
    }

    override fun groups(): Set<String> = byId.values.map { it.group }.toSortedSet()
}
