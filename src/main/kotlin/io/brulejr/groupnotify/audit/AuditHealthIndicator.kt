/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import java.time.Clock
import java.time.Duration

/** Up when the history answers; reports the past day's deliveries. */
class AuditHealthIndicator(private val service: AuditService, private val clock: Clock) : HealthIndicator {

    override fun health(): Health = try {
        val counts = service.counts(clock.instant().minus(Duration.ofDays(1)))
        Health.up()
            .withDetail("sentLastDay", counts[AuditEventType.SENT] ?: 0)
            .withDetail("failedLastDay", counts[AuditEventType.FAILED] ?: 0)
            .withDetail("skippedLastDay", counts[AuditEventType.SKIPPED] ?: 0)
            .build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
