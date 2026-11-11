/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up, with each plugin's readiness. A plugin that is not ready does not take the
 * application down: a group may simply not use it.
 */
class DeliveryHealthIndicator(private val service: DeliveryService) : HealthIndicator {
    override fun health(): Health {
        val stats = service.stats()
        return Health.up()
            .withDetail("plugins", service.plugins().associate { it.id to (if (it.ready) "ready" else "not ready: ${it.reason}") })
            .withDetail("delivered", stats.delivered)
            .withDetail("failed", stats.failed)
            .withDetail("retries", stats.retries)
            .build()
    }
}
