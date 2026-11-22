/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import java.time.Clock
import java.time.Duration

/**
 * Where alerts go: how many instances receive them, how many were posted in the last
 * day, and which groups with instances have none marked, whose failures nobody is told of.
 */
class AlertHealthIndicator(private val alerts: AlertService, private val datafill: AlertDatafill, private val clock: Clock) : HealthIndicator {

    override fun health(): Health = try {
        val (instances, lastDay) = alerts.counts(clock.instant().minus(Duration.ofDays(1)))
        Health.up()
            .withDetail("enabled", datafill.enabled)
            .withDetail("alertInstances", instances)
            .withDetail("alertedLastDay", lastDay)
            .withDetail("groupsWithoutAlerts", alerts.groupsWithoutAlerts().sorted())
            .build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
