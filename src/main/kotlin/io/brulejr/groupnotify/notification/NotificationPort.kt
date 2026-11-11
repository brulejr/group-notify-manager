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

/** Where notifications are kept. Storage only: [NotificationService] checks and authorises. */
interface NotificationPort {

    fun find(id: UUID): Notification?

    /** Notifications in [groups], or every one when null. */
    fun list(groups: Set<String>?): List<Notification>

    /** Stores [notification] with its deliveries, replacing any earlier version. */
    fun save(notification: Notification)

    fun delete(id: UUID): Boolean

    /** Active notifications whose next run is at or before [now], earliest first. */
    fun due(now: Instant, limit: Int): List<Notification>

    /**
     * Moves a due notification on to its next run, but only if it is still active and
     * still due at [expected]: an edit or pause since it was read wins, and the run is
     * abandoned. Returns whether it was moved.
     */
    fun advance(id: UUID, expected: Instant, nextRunAt: Instant?, status: NotificationStatus, lastRunAt: Instant): Boolean

    /** Every group that has a notification. */
    fun groups(): Set<String>
}
