/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.group

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/** Always up: groups live in Authelia. Reports which are offered. */
class GroupHealthIndicator(private val datafill: GroupDatafill) : HealthIndicator {
    override fun health(): Health = Health.up().withDetail("pattern", datafill.pattern).withDetail("excluded", datafill.exclude.size).build()
}
