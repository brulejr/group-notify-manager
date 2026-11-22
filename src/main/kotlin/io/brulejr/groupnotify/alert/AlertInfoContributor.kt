/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How alerts are set up. */
class AlertInfoContributor(private val datafill: AlertDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "alerts",
            mapOf(
                "enabled" to datafill.enabled,
                "quietPeriod" to datafill.quietPeriod.toString(),
                "recovery" to datafill.recovery,
                "skipped" to datafill.skipped,
                "links" to datafill.consoleUrl.isNotBlank(),
            ),
        )
    }
}
