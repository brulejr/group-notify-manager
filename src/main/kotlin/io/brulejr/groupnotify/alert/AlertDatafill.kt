/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property enabled whether failures are posted to the groups' alerts instances at all.
 * @property quietPeriod after an alert about an instance (or a message's skipped run),
 *   further ones are held back this long and counted into the next.
 * @property recovery whether to say so when a failing instance sends again.
 * @property skipped whether a run skipped for being too late is alerted, like a failure.
 * @property consoleUrl the console's public address, e.g. `https://notify.example.org`;
 *   when set, an alert links to the message or event.
 */
@ConfigurationProperties("notify.alerts")
data class AlertDatafill(
    val enabled: Boolean = true,
    val quietPeriod: Duration = Duration.ofHours(1),
    val recovery: Boolean = true,
    val skipped: Boolean = true,
    val consoleUrl: String = "",
) {
    init {
        require(!quietPeriod.isNegative && quietPeriod <= Duration.ofDays(7)) { "notify.alerts.quiet-period must be between 0 and 7d" }
        require(consoleUrl.isBlank() || Regex("https?://\\S+").matches(consoleUrl)) { "notify.alerts.console-url must be an http or https URL" }
    }
}
