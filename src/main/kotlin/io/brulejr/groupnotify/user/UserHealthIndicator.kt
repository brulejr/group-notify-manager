/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/** How many people use the system: seen in the last day, and ever. */
class UserHealthIndicator(private val users: UserService) : HealthIndicator {

    override fun health(): Health = try {
        val (lastDay, all) = users.counts()
        Health.up().withDetail("seenLastDay", lastDay).withDetail("known", all).build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
