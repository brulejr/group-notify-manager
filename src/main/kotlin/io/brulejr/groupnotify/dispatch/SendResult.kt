/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

/** Outcome of Send now. */
sealed interface SendResult {

    /** Sent; each report says whether its delivery got through. */
    data class Sent(val reports: List<DeliveryReport>) : SendResult {
        val failed get() = reports.count { !it.outcome.delivered }
    }

    /** No such notification, or not one the user may see. */
    data object NotFound : SendResult

    /** The user may see it but not send it. */
    data object Forbidden : SendResult

    /** It has no enabled delivery, or not the one asked for. */
    data object NothingToSend : SendResult
}
