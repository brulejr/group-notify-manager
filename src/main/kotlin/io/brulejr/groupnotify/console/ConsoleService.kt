/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console

import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.event.EventService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.security.NotifyUser
import java.time.Clock
import java.time.Duration

/** What the console's front page shows a user: their groups' notifications and events at a glance. */
class ConsoleService(
    private val notifications: NotificationService,
    private val events: EventService,
    private val audit: AuditService,
    private val deliveries: DeliveryService,
    private val groups: GroupService,
    private val clock: Clock,
) {

    fun overview(user: NotifyUser): ConsoleOverview {
        val all = notifications.list(user)
        val byStatus = all.groupingBy { it.status }.eachCount()
        val counts = audit.counts(user, clock.instant().minus(Duration.ofDays(1)))
        return ConsoleOverview(
            active = byStatus[NotificationStatus.ACTIVE] ?: 0,
            paused = byStatus[NotificationStatus.PAUSED] ?: 0,
            completed = byStatus[NotificationStatus.COMPLETED] ?: 0,
            sentLastDay = counts[AuditEventType.SENT] ?: 0,
            failedLastDay = counts[AuditEventType.FAILED] ?: 0,
            upcoming = upcoming(user, UPCOMING).toMutableList(),
            events = nextEvents(user, EVENTS).toMutableList(),
            recent = audit.history(user, AuditQuery(limit = 10)).toMutableList(),
            plugins = deliveries.plugins().toMutableList(),
            groups = groups.viewable(user, notifications.groupsInUse() + events.groupsInUse()).toMutableList(),
        )
    }

    /** The next [count] things to be sent, notifications and event reminders together, soonest first. */
    private fun upcoming(user: NotifyUser, count: Int): List<ComingUp> {
        val runs = notifications.upcoming(user, count).map { n ->
            ComingUp(checkNotNull(n.nextRunAt), n.schedule.zone, n.title, n.subject, n.group, n.schedule.kind.label, "/ui/messages/${n.id}", event = false)
        }
        val reminders = events.list(user).flatMap { e ->
            events.upcoming(e, count).map { pending ->
                val reminder = e.reminder(pending.reminder)
                // As that reminder will send it, its placeholders filled.
                val subject = events.message(e, pending.occurrence, reminder, groups.displayName(e.group), pending.sendAt).subject
                ComingUp(
                    pending.sendAt, e.schedule.zone, e.title, subject,
                    e.group, reminder?.describe() ?: "reminder", "/ui/events/${e.id}", event = true,
                )
            }
        }
        return (runs + reminders).sortedBy { it.at }.take(count)
    }

    /** Each event's next occurrence, soonest first. */
    private fun nextEvents(user: NotifyUser, count: Int): List<NextEvent> = events.list(user).mapNotNull { e ->
        val at = events.occurrences(e, 1).firstOrNull() ?: return@mapNotNull null
        NextEvent(at, e.schedule.zone, e.title, e.group, "/ui/events/${e.id}", e.next?.let { e.reminder(it.reminder)?.describe() }, e.nextRunAt)
    }.sortedBy { it.at }.take(count)

    private companion object {
        const val UPCOMING = 8
        const val EVENTS = 5
    }
}
