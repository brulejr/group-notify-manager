/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How placeholders print dates and times. Each notification's own time zone is used.
 *
 * @property dateFormat a [DateTimeFormatter] pattern for `{{date}}`.
 * @property timeFormat a [DateTimeFormatter] pattern for `{{time}}`.
 * @property locale a BCP 47 tag, e.g. `en-US`, for day and month names.
 */
@ConfigurationProperties("notify.content")
data class ContentDatafill(
    val dateFormat: String = "EEEE, MMMM d, yyyy",
    val timeFormat: String = "h:mm a z",
    val locale: String = "en-US",
) {
    init {
        require(runCatching { DateTimeFormatter.ofPattern(dateFormat) }.isSuccess) { "notify.content.date-format is not a date pattern" }
        require(runCatching { DateTimeFormatter.ofPattern(timeFormat) }.isSuccess) { "notify.content.time-format is not a time pattern" }
    }

    val javaLocale: Locale get() = Locale.forLanguageTag(locale)
}
