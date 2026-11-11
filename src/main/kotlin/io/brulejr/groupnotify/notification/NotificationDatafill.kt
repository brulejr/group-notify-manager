/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import io.brulejr.groupnotify.platform.StoreType
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.ZoneId

/**
 * @property store where notifications are kept.
 * @property defaultZone the time zone a new notification's times are in unless its
 *   author picks another.
 * @property maxDeliveries per notification.
 * @property maxBodyLength characters, Markdown or HTML.
 */
@ConfigurationProperties("notify.notifications")
data class NotificationDatafill(
    val store: StoreType = StoreType.POSTGRES,
    val defaultZone: String = "UTC",
    val maxDeliveries: Int = 10,
    val maxBodyLength: Int = 100_000,
) {
    init {
        require(runCatching { ZoneId.of(defaultZone) }.isSuccess) { "notify.notifications.default-zone is not a time zone: $defaultZone" }
        require(maxDeliveries in 1..100) { "notify.notifications.max-deliveries must be between 1 and 100" }
        require(maxBodyLength >= 1000) { "notify.notifications.max-body-length must be at least 1000" }
    }

    val zone: ZoneId get() = ZoneId.of(defaultZone)
}
