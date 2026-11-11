/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import org.springframework.scheduling.support.CronExpression

/**
 * A recurring schedule's cron expression, as the author writes it.
 *
 * Accepts the usual five fields (`minute hour day month weekday`, e.g. `0 18 * * TUE`),
 * Spring's six with seconds first, and the macros `@daily`, `@weekly`, `@monthly`,
 * `@yearly` and `@hourly`. Spring's extensions work too: `L` for the last day of the
 * month, `TUE#2` for the second Tuesday.
 */
object CronSpec {

    /** The expression in Spring's six-field form, or throws [IllegalArgumentException]. */
    fun parse(text: String): CronExpression = CronExpression.parse(normalize(text))

    fun normalize(text: String): String {
        val trimmed = text.trim().replace(Regex("\\s+"), " ")
        require(trimmed.isNotEmpty()) { "is required" }
        if (trimmed.startsWith("@")) return trimmed.lowercase()
        return when (trimmed.split(' ').size) {
            5 -> "0 $trimmed"
            6 -> trimmed
            else -> throw IllegalArgumentException("needs five fields: minute hour day-of-month month day-of-week")
        }
    }

    /** Why [text] is not a usable expression, or null if it is. */
    fun problem(text: String): String? = runCatching { parse(text) }.exceptionOrNull()?.let { e ->
        e.message?.takeIf { it.isNotBlank() }?.substringBefore(" in cron expression")?.let { "is not a valid cron expression: $it" }
            ?: "is not a valid cron expression"
    }

    /** Ready-made expressions the console offers. */
    val EXAMPLES = linkedMapOf(
        "0 9 * * *" to "every day at 9:00",
        "0 18 * * TUE" to "every Tuesday at 18:00",
        "0 9 * * MON-FRI" to "weekdays at 9:00",
        "0 19 * * TUE#2" to "the second Tuesday of each month at 19:00",
        "0 12 1 * *" to "the 1st of each month at noon",
        "0 17 L * *" to "the last day of each month at 17:00",
        "0 8 1 1 *" to "every 1 January at 8:00",
    )
}
