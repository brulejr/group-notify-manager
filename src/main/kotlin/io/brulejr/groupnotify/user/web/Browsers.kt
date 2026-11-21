/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user.web

/**
 * A User-Agent in a few words, e.g. `Firefox on Linux`, for a list. Rough by design: the
 * full string is shown on the person's page.
 */
object Browsers {

    private val BROWSERS = listOf("Edg/" to "Edge", "Firefox/" to "Firefox", "Chrome/" to "Chrome", "Safari/" to "Safari", "curl/" to "curl", "bruno" to "Bruno")
    private val SYSTEMS = listOf("Android" to "Android", "iPhone" to "iPhone", "iPad" to "iPad", "Windows" to "Windows", "Mac OS" to "macOS", "Linux" to "Linux")

    fun describe(agent: String?): String? {
        if (agent.isNullOrBlank()) return null
        val browser = BROWSERS.firstOrNull { (mark, _) -> agent.contains(mark, ignoreCase = true) }?.second
        val system = SYSTEMS.firstOrNull { (mark, _) -> agent.contains(mark, ignoreCase = true) }?.second
        return when {
            browser != null && system != null -> "$browser on $system"
            else -> browser ?: system ?: agent.substringBefore(' ').take(40)
        }
    }
}
