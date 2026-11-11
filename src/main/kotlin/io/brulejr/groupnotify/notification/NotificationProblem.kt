/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

/**
 * One thing wrong with a draft. A delivery's field is named by its index and key, e.g.
 * `deliveries[1].target.webhookUrl`, so the console can show it against that input.
 */
data class NotificationProblem(val field: String, val reason: String)
