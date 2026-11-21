/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where user records are kept and how coarse they are. */
class UserInfoContributor(private val datafill: UserDatafill) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "users",
            mapOf(
                "store" to datafill.store,
                "touchInterval" to datafill.touchInterval.toString(),
                "visitGap" to datafill.visitGap.toString(),
                "lldap" to datafill.lldapUrl.isNotBlank(),
            ),
        )
    }
}
