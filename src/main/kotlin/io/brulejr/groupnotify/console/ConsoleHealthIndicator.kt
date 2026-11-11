/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/** Up when the console's templates are on the class path. */
class ConsoleHealthIndicator : HealthIndicator {
    override fun health(): Health =
        if (javaClass.classLoader.getResource("templates/ui/layout.html") != null) Health.up().build()
        else Health.down().withDetail("error", "console templates are missing").build()
}
