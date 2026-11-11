/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.discord

/**
 * Who a post pings. Discord only pings what the payload's `allowed_mentions` permits,
 * so the text and the permission are built together here.
 */
data class Mention(val content: String, val parse: List<String>, val roles: List<String>) {
    companion object {
        private val ROLE = Regex("<?@?&?(\\d{5,25})>?")

        /** `@everyone`, `@here`, or a role as its id or `<@&id>`; null for anything else. */
        fun parse(text: String): Mention? {
            val trimmed = text.trim()
            return when {
                trimmed.equals("@everyone", ignoreCase = true) -> Mention("@everyone", listOf("everyone"), emptyList())
                trimmed.equals("@here", ignoreCase = true) -> Mention("@here", listOf("everyone"), emptyList())
                else -> ROLE.matchEntire(trimmed)?.groupValues?.get(1)?.let { Mention("<@&$it>", emptyList(), listOf(it)) }
            }
        }
    }
}
