/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** How users are signed in and which groups carry roles. */
class SecurityInfoContributor(private val datafill: SecurityDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "security",
            mapOf(
                "mode" to datafill.mode,
                "adminGroup" to datafill.adminGroup,
                "editorGroup" to (datafill.editorGroup ?: "any member"),
                "trustedProxies" to datafill.forwardAuth.trustedProxies,
            ),
        )
    }
}
