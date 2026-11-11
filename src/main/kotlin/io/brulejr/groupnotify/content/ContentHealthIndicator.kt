/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import io.brulejr.groupnotify.notification.BodyFormat
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/** Up when a sample body renders: the Markdown and HTML libraries are present and working. */
class ContentHealthIndicator(private val service: ContentService) : HealthIndicator {
    override fun health(): Health = try {
        check(service.html("**ok**", BodyFormat.MARKDOWN).contains("<strong>ok</strong>")) { "Markdown did not render" }
        Health.up().build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
