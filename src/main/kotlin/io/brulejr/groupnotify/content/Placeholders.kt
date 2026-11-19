/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import org.springframework.web.util.HtmlUtils

/**
 * `{{name}}` placeholders in a subject or body, such as `{{date}}`, or a dotted name for
 * an event's own, such as `{{event.location}}`. A name with no value is left as written,
 * so a typo shows in the message rather than vanishing.
 */
object Placeholders {

    private val PLACEHOLDER = Regex("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)?)\\s*}}")

    /** [text] with every use of the placeholder [name] replaced by [value] as it is: no escaping. */
    fun put(text: String, name: String, value: String): String =
        PLACEHOLDER.replace(text) { match -> if (match.groupValues[1] == name) value else match.value }

    /** Whether [text] uses the placeholder [name] anywhere. */
    fun uses(text: String, name: String): Boolean = PLACEHOLDER.findAll(text).any { it.groupValues[1] == name }

    /** [text] with each placeholder replaced; values are escaped when [html] is true. */
    fun fill(text: String, values: Map<String, String>, html: Boolean = false): String =
        PLACEHOLDER.replace(text) { match ->
            values[match.groupValues[1]]?.let { if (html) HtmlUtils.htmlEscape(it) else it } ?: match.value
        }

    /** Every placeholder an author can use, with what it stands for. */
    val NAMES = linkedMapOf(
        "title" to "the notification's title",
        "group" to "the group's display name",
        "date" to "the date it is sent, e.g. Tuesday, November 10, 2026",
        "time" to "the time it is sent, e.g. 6:00 PM EST",
        "weekday" to "the day it is sent, e.g. Tuesday",
        "datetime" to "the date and time it is sent",
    )

    /** An event's own placeholders, beside [NAMES] and one `{{event.<name>}}` per property. */
    val EVENT_NAMES = linkedMapOf(
        "event.date" to "the date the event is on",
        "event.time" to "the time it starts",
        "event.weekday" to "the day it is on",
        "event.datetime" to "its date and time",
        "event.when" to "when it is, from this reminder: in 2 hours, now, 1 day ago",
        "occurrence.note" to "this occurrence's own note, if it has one; otherwise added after the body",
    )
}
