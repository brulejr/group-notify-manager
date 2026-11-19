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

/** Where events are kept. Storage only: [EventService] checks and authorises. */
interface EventPort {

    fun find(id: UUID): Event?

    /** Events in [groups], or every one when null. */
    fun list(groups: Set<String>?): List<Event>

    /** Stores [event] with its deliveries, replacing any earlier version. */
    fun save(event: Event)

    fun delete(id: UUID): Boolean

    /** Active events whose next reminder is due at or before [now], earliest first. */
    fun due(now: Instant, limit: Int): List<Event>

    /**
     * Moves a due event on to its next reminder, but only if it is still active and still
     * due at [expected]: an edit or pause since it was read wins. Returns whether it moved.
     */
    fun advance(id: UUID, expected: Instant, next: PendingReminder?, status: NotificationStatus, sentAt: Instant): Boolean

    /** Every group that has an event. */
    fun groups(): Set<String>
}
