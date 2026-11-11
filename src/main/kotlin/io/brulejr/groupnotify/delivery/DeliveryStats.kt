/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

/** Deliveries made since startup, by outcome. */
data class DeliveryStats(val delivered: Long, val failed: Long, val retries: Long)
