/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import io.brulejr.groupnotify.platform.StoreType
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property store where user records are kept.
 * @property touchInterval how often, at most, a person's record is written while they
 *   use the console: last-seen times are this coarse, and requests stay cheap.
 * @property visitGap requests further apart than this start a new visit.
 * @property lldapUrl LLDAP's web address, e.g. `https://lldap.example.org`; when set, each
 *   person links to their page there, where their account and groups are managed.
 */
@ConfigurationProperties("notify.users")
data class UserDatafill(
    val store: StoreType = StoreType.POSTGRES,
    val touchInterval: Duration = Duration.ofMinutes(5),
    val visitGap: Duration = Duration.ofMinutes(30),
    val lldapUrl: String = "",
) {
    init {
        require(!touchInterval.isNegative && touchInterval <= Duration.ofHours(1)) { "notify.users.touch-interval must be between 0 and 1h" }
        require(visitGap > touchInterval) { "notify.users.visit-gap must be longer than notify.users.touch-interval" }
        require(lldapUrl.isBlank() || Regex("https?://\\S+").matches(lldapUrl)) { "notify.users.lldap-url must be an http or https URL" }
    }
}
