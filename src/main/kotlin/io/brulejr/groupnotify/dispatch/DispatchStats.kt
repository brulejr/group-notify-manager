/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import java.time.Instant

/**
 * The scheduler since startup.
 *
 * @property polls times it looked for due notifications.
 * @property runs notifications it sent; [skipped] were missed by more than the grace.
 * @property lastPollAt null until the first poll.
 * @property lastError why the last poll failed, if it did; cleared by one that succeeds.
 */
data class DispatchStats(
    val polls: Long,
    val runs: Long,
    val skipped: Long,
    val deliveriesSent: Long,
    val deliveriesFailed: Long,
    val lastPollAt: Instant?,
    val lastError: String?,
)
