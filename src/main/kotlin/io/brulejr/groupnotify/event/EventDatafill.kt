/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.platform.StoreType
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property store where events are kept.
 * @property maxReminders per event.
 * @property maxProperties per event.
 * @property maxDeliveries per event.
 * @property maxBodyLength characters, Markdown or HTML.
 */
@ConfigurationProperties("notify.events")
data class EventDatafill(
    val store: StoreType = StoreType.POSTGRES,
    val maxReminders: Int = 20,
    val maxProperties: Int = 30,
    val maxDeliveries: Int = 10,
    val maxBodyLength: Int = 100_000,
) {
    init {
        require(maxReminders in 1..100) { "notify.events.max-reminders must be between 1 and 100" }
        require(maxProperties in 0..200) { "notify.events.max-properties must be between 0 and 200" }
        require(maxDeliveries in 1..100) { "notify.events.max-deliveries must be between 1 and 100" }
        require(maxBodyLength >= 1000) { "notify.events.max-body-length must be at least 1000" }
    }
}
