/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

/** What came of one delivery, as recorded in the audit trail. */
sealed interface DeliveryResult {

    /**
     * Accepted by the service at the other end.
     *
     * @property detail shown in the audit trail, e.g. `2 of 2 numbers`.
     * @property reference the service's own id for it, e.g. a message SID, when it has one.
     */
    data class Delivered(val detail: String = "", val reference: String? = null) : DeliveryResult

    /**
     * Not delivered.
     *
     * @property retryable worth trying again shortly: a timeout or a 5xx, not a bad
     *   address. The application retries up to `notify.delivery.max-attempts` times.
     */
    data class Failed(val reason: String, val retryable: Boolean = false) : DeliveryResult
}
