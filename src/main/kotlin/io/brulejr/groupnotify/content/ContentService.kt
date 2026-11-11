/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.plugin.NotificationMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A notification's body in every form: the author writes Markdown or HTML, plugins get
 * Markdown, sanitised HTML and plain text, all made here from that one source, with
 * placeholders filled in.
 */
class ContentService(private val datafill: ContentDatafill) {

    private val markdown = MarkdownRenderer()
    private val dateFormat = DateTimeFormatter.ofPattern(datafill.dateFormat, datafill.javaLocale)
    private val timeFormat = DateTimeFormatter.ofPattern(datafill.timeFormat, datafill.javaLocale)
    private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", datafill.javaLocale)

    /** [body] as HTML that is safe to show or send. */
    fun html(body: String, format: BodyFormat): String = HtmlSanitizer.clean(
        when (format) {
            BodyFormat.MARKDOWN -> markdown.toHtml(body)
            BodyFormat.HTML -> body
        },
    )

    fun markdown(body: String, format: BodyFormat): String = when (format) {
        BodyFormat.MARKDOWN -> body.trim()
        BodyFormat.HTML -> HtmlFlattener.toMarkdown(HtmlSanitizer.clean(body))
    }

    fun text(body: String, format: BodyFormat): String = HtmlFlattener.toText(html(body, format))

    /**
     * [body] rewritten for the other editor, when an author switches. Markdown becomes
     * HTML faithfully; HTML becomes Markdown as closely as Markdown allows, so colours
     * and alignment are lost.
     */
    fun convert(body: String, from: BodyFormat, to: BodyFormat): String = when {
        from == to -> body
        to == BodyFormat.HTML -> html(body, from)
        else -> markdown(body, from)
    }

    /** What the plugins are handed for [notification] as sent at [at]. */
    fun render(notification: Notification, groupName: String, at: Instant): NotificationMessage {
        val values = placeholders(notification.title, groupName, notification.schedule.zone, at)
        val subject = Placeholders.fill(notification.subject, values).replace(Regex("\\s+"), " ").trim()
        val body = Placeholders.fill(notification.body, values, html = notification.format == BodyFormat.HTML)
        val html = html(body, notification.format)
        return NotificationMessage(
            notificationId = notification.id.toString(),
            title = notification.title,
            group = notification.group,
            subject = subject,
            markdown = markdown(body, notification.format),
            html = html,
            text = HtmlFlattener.toText(html),
            scheduledFor = at,
        )
    }

    fun placeholders(title: String, groupName: String, zone: ZoneId, at: Instant): Map<String, String> {
        val local = at.atZone(zone)
        return mapOf(
            "title" to title,
            "group" to groupName,
            "date" to dateFormat.format(local),
            "time" to timeFormat.format(local),
            "weekday" to weekdayFormat.format(local),
            "datetime" to "${dateFormat.format(local)} ${timeFormat.format(local)}",
        )
    }
}
