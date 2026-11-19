/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where events are kept and their limits. */
class EventInfoContributor(private val datafill: EventDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "events",
            mapOf(
                "store" to datafill.store,
                "maxReminders" to datafill.maxReminders,
                "maxProperties" to datafill.maxProperties,
                "maxDeliveries" to datafill.maxDeliveries,
            ),
        )
    }
}
