/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where notifications are kept, and the limits on them. */
class NotificationInfoContributor(private val datafill: NotificationDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "notifications",
            mapOf(
                "store" to datafill.store,
                "defaultZone" to datafill.defaultZone,
                "maxDeliveries" to datafill.maxDeliveries,
                "maxBodyLength" to datafill.maxBodyLength,
            ),
        )
    }
}
