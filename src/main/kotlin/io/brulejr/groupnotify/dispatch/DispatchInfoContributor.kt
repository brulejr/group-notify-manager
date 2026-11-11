/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How the scheduler is set up. */
class DispatchInfoContributor(private val datafill: DispatchDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "dispatch",
            mapOf(
                "pollInterval" to datafill.pollInterval.toString(),
                "misfireGrace" to datafill.misfireGrace.toString(),
                "batchSize" to datafill.batchSize,
                "threads" to datafill.threads,
            ),
        )
    }
}
