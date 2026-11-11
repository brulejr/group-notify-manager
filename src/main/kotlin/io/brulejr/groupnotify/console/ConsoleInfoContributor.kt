/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How the console is set up. */
class ConsoleInfoContributor(private val datafill: ConsoleDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "console",
            mapOf("title" to datafill.title, "refreshInterval" to datafill.refreshInterval.toString(), "pageSize" to datafill.pageSize),
        )
    }
}
