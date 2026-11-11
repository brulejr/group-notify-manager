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
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.security.NotifyUser
import java.time.Clock
import java.time.Duration

/** What the console's front page shows a user: their groups' notifications at a glance. */
class ConsoleService(
    private val notifications: NotificationService,
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
            upcoming = notifications.upcoming(user, 8).toMutableList(),
            recent = audit.history(user, AuditQuery(limit = 10)).toMutableList(),
            plugins = deliveries.plugins().toMutableList(),
            groups = groups.viewable(user, notifications.groupsInUse()).toMutableList(),
        )
    }
}
