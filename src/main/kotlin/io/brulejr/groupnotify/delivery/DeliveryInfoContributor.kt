/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Which plugins are loaded and from where, and how deliveries are retried. */
class DeliveryInfoContributor(private val service: DeliveryService, private val datafill: DeliveryDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "delivery",
            mapOf(
                "plugins" to service.plugins().map { mapOf("id" to it.id, "name" to it.name, "source" to it.source, "settings" to it.configuredSettings) },
                "disabled" to datafill.disabled,
                "maxAttempts" to datafill.maxAttempts,
                "retryBackoff" to datafill.retryBackoff.toString(),
                "mailFrom" to datafill.mail.from,
            ),
        )
    }
}
