/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.group

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Which groups are notification groups, and what they are called. */
class GroupInfoContributor(private val datafill: GroupDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail("groups", mapOf("pattern" to datafill.pattern, "exclude" to datafill.exclude, "names" to datafill.names))
    }
}
