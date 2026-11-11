/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * The scheduler that sends notifications when they are due. Turned off with
 * `notify.dispatch.enabled=false`, as the tests do; Send now still works then.
 *
 * @property pollInterval how often it looks for due notifications, and so how late one
 *   can be sent.
 * @property misfireGrace a run missed by longer than this, while the application was
 *   down say, is skipped and recorded rather than sent late. A reminder for a meeting
 *   that has already happened helps nobody.
 * @property batchSize due notifications taken per poll; the rest wait for the next.
 * @property threads notifications sent at once. Each one's deliveries go in order.
 */
@ConfigurationProperties("notify.dispatch")
data class DispatchDatafill(
    val pollInterval: Duration = Duration.ofSeconds(15),
    val misfireGrace: Duration = Duration.ofHours(1),
    val batchSize: Int = 50,
    val threads: Int = 4,
) {
    init {
        require(pollInterval >= Duration.ofSeconds(1)) { "notify.dispatch.poll-interval must be at least 1s" }
        require(misfireGrace >= pollInterval) { "notify.dispatch.misfire-grace must be at least the poll interval" }
        require(batchSize in 1..1000) { "notify.dispatch.batch-size must be between 1 and 1000" }
        require(threads in 1..32) { "notify.dispatch.threads must be between 1 and 32" }
    }
}
