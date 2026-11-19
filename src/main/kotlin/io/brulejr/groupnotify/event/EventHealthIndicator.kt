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
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import java.time.Clock

/** How many events there are, by status, how many reminders are overdue, and when the next is due. */
class EventHealthIndicator(private val store: EventPort, private val clock: Clock) : HealthIndicator {

    override fun health(): Health = try {
        val all = store.list(null)
        val byStatus = all.groupingBy { it.status }.eachCount()
        Health.up()
            .withDetail("active", byStatus[NotificationStatus.ACTIVE] ?: 0)
            .withDetail("paused", byStatus[NotificationStatus.PAUSED] ?: 0)
            .withDetail("completed", byStatus[NotificationStatus.COMPLETED] ?: 0)
            .withDetail("overdue", store.due(clock.instant(), 1000).size)
            .withDetail("nextReminderAt", all.mapNotNull { it.nextRunAt }.minOrNull()?.toString() ?: "none")
            .build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
