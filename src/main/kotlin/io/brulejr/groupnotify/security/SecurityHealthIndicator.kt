/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up, with how users are signed in. A development user in place of forward-auth is
 * reported as such, so it is noticed if it ever reaches a deployed instance.
 */
class SecurityHealthIndicator(private val datafill: SecurityDatafill) : HealthIndicator {
    override fun health(): Health = Health.up()
        .withDetail("mode", datafill.mode)
        .withDetail("devUser", datafill.devUser?.username ?: "none")
        .build()
}
