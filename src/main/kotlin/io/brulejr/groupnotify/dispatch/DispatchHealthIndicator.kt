/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import java.time.Clock
import java.time.Duration

/**
 * Down when the last poll failed, or when polls have stopped: a scheduler that has gone
 * quiet sends nothing, which is otherwise only noticed when a reminder fails to arrive.
 * Up before the first poll, and when the scheduler is switched off.
 */
class DispatchHealthIndicator(
    private val service: DispatchService,
    private val datafill: DispatchDatafill,
    private val clock: Clock,
) : HealthIndicator {

    override fun health(): Health {
        val stats = service.stats()
        val builder = stats.lastError?.let { Health.down().withDetail("error", it) } ?: Health.up()
        val last = stats.lastPollAt
        if (last != null && Duration.between(last, clock.instant()) > datafill.pollInterval.multipliedBy(4)) {
            builder.down().withDetail("stalled", "no poll since $last")
        }
        return builder
            .withDetail("polls", stats.polls)
            .withDetail("runs", stats.runs)
            .withDetail("skipped", stats.skipped)
            .withDetail("deliveriesSent", stats.deliveriesSent)
            .withDetail("deliveriesFailed", stats.deliveriesFailed)
            .withDetail("lastPollAt", last?.toString() ?: "never")
            .build()
    }
}
