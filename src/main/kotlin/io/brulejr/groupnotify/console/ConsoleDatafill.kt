/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * The console, served under `/ui`.
 *
 * @property title what the console calls itself, e.g. `Radio Club Notices`.
 * @property refreshInterval how often the dashboard reloads itself.
 * @property pageSize rows per page of history.
 */
@ConfigurationProperties("notify.console")
data class ConsoleDatafill(
    val title: String = "Group Notify",
    val refreshInterval: Duration = Duration.ofSeconds(30),
    val pageSize: Int = 50,
) {
    init {
        require(refreshInterval >= Duration.ofSeconds(5)) { "notify.console.refresh-interval must be at least 5s" }
        require(pageSize in 10..500) { "notify.console.page-size must be between 10 and 500" }
    }
}
