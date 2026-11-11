/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How placeholders print dates and times, and which there are. */
class ContentInfoContributor(private val datafill: ContentDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "content",
            mapOf(
                "dateFormat" to datafill.dateFormat,
                "timeFormat" to datafill.timeFormat,
                "locale" to datafill.locale,
                "placeholders" to Placeholders.NAMES.keys,
            ),
        )
    }
}
