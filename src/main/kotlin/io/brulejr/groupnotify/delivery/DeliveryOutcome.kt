/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.DeliveryResult

/** One delivery after every attempt: the last result, and how many tries it took. */
data class DeliveryOutcome(val result: DeliveryResult, val attempts: Int) {
    val delivered get() = result is DeliveryResult.Delivered

    /** What the plugin said: the delivered detail, or why it failed. */
    val detail: String get() = when (val r = result) {
        is DeliveryResult.Delivered -> r.detail
        is DeliveryResult.Failed -> r.reason
    }
}
