/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.group

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Which of a user's Authelia groups are notification groups. Groups are created and
 * joined in LLDAP; this only decides which of them the console offers.
 *
 * @property pattern a regular expression a group's whole name must match, e.g.
 *   `notify_.*`. The default offers every group the user is in.
 * @property exclude groups never offered, whatever the pattern says, e.g. `homelab_users`.
 * @property names what to call a group in the console, e.g. `notify_club: Radio Club`.
 */
@ConfigurationProperties("notify.groups")
data class GroupDatafill(
    val pattern: String = ".*",
    val exclude: Set<String> = emptySet(),
    val names: Map<String, String> = emptyMap(),
) {
    init {
        require(runCatching { Regex(pattern) }.isSuccess) { "notify.groups.pattern is not a regular expression: $pattern" }
    }
}
