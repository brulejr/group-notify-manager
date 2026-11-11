/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where the history is kept, and for how long. */
class AuditInfoContributor(private val datafill: AuditDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail("audit", mapOf("store" to datafill.store, "retention" to datafill.retention.toString()))
    }
}
