/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.NotificationProblem
import io.brulejr.groupnotify.notification.ScheduleKind
import org.springframework.util.MultiValueMap
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The notification form as submitted, kept as the strings typed so a refused form is
 * redrawn exactly as it was. A delivery's inputs are named `deliveries[n].instance`,
 * `deliveries[n].overrides.<key>` and so on; n is only a row number in the page and may
 * have gaps where rows were removed.
 */
data class NotificationForm(
    val group: String = "",
    val title: String = "",
    val subject: String = "",
    val body: String = "",
    val format: BodyFormat = BodyFormat.MARKDOWN,
    val kind: ScheduleKind = ScheduleKind.ONCE,
    val zone: String = "",
    val runAt: String = "",
    val cron: String = "",
    val until: String = "",
    val pattern: String = "",
    val paused: Boolean = false,
    val deliveries: List<Delivery> = emptyList(),
) {
    /** @property overrides the values typed for the instance's overridable fields. */
    data class Delivery(
        val id: String = "",
        val instance: String,
        val enabled: Boolean = true,
        val overrides: Map<String, String> = emptyMap(),
    )

    /** The draft, with any time that would not parse reported against its input. */
    fun toDraft(): Pair<NotificationDraft, List<NotificationProblem>> {
        val problems = mutableListOf<NotificationProblem>()
        val parsedRunAt = runAt.trim().takeIf { it.isNotEmpty() && kind == ScheduleKind.ONCE }?.let {
            runCatching { LocalDateTime.parse(it) }.getOrNull().also { parsed -> if (parsed == null) problems += NotificationProblem("runAt", "is not a date and time") }
        }
        val parsedUntil = until.trim().takeIf { it.isNotEmpty() && kind == ScheduleKind.RECURRING }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull().also { parsed -> if (parsed == null) problems += NotificationProblem("until", "is not a date") }
        }
        val draft = NotificationDraft(
            group = group, title = title, subject = subject, body = body, format = format, kind = kind, zone = zone,
            runAt = parsedRunAt, cron = if (kind == ScheduleKind.RECURRING) cron else "", until = parsedUntil,
            pattern = if (kind == ScheduleKind.PATTERN) pattern else "",
            deliveries = deliveries.map { NotificationDraft.Delivery(it.id.ifBlank { null }, it.instance, it.overrides, it.enabled) },
            paused = paused,
        )
        return draft to problems
    }

    companion object {
        private val DELIVERY = Regex("deliveries\\[(\\d+)]\\.(.+)")
        private val LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        fun from(params: MultiValueMap<String, String>): NotificationForm {
            fun one(name: String) = params.getFirst(name).orEmpty()
            val rows = sortedMapOf<Int, MutableMap<String, String>>()
            params.forEach { (name, values) ->
                DELIVERY.matchEntire(name)?.let { match ->
                    // A checkbox is sent with a hidden "false" before it, so the last value wins.
                    rows.getOrPut(match.groupValues[1].toInt()) { mutableMapOf() }[match.groupValues[2]] = values.last()
                }
            }
            return NotificationForm(
                group = one("group"),
                title = one("title"),
                subject = one("subject"),
                body = one("body"),
                format = runCatching { BodyFormat.valueOf(one("format")) }.getOrDefault(BodyFormat.MARKDOWN),
                kind = runCatching { ScheduleKind.valueOf(one("kind")) }.getOrDefault(ScheduleKind.ONCE),
                zone = one("zone"),
                runAt = one("runAt"),
                cron = one("cron"),
                until = one("until"),
                pattern = one("pattern"),
                paused = params["paused"]?.lastOrNull() == "true",
                deliveries = rows.values.filter { !it["instance"].isNullOrBlank() }.map { row ->
                    Delivery(
                        id = row["id"].orEmpty(),
                        instance = row.getValue("instance"),
                        enabled = row["enabled"] != "false",
                        overrides = row.filterKeys { it.startsWith("overrides.") }.mapKeys { it.key.removePrefix("overrides.") },
                    )
                },
            )
        }

        /** A stored notification as the form shows it. It holds no secret: those stay on the instances. */
        fun of(n: Notification) = NotificationForm(
            group = n.group,
            title = n.title,
            subject = n.subject,
            body = n.body,
            format = n.format,
            kind = n.schedule.kind,
            zone = n.schedule.zone.id,
            runAt = n.schedule.runAt?.atZone(n.schedule.zone)?.format(LOCAL).orEmpty(),
            cron = n.schedule.cron.orEmpty(),
            // A pattern's end is part of its words.
            until = n.schedule.until?.takeIf { n.schedule.kind == ScheduleKind.RECURRING }?.atZone(n.schedule.zone)?.toLocalDate()?.toString().orEmpty(),
            pattern = n.schedule.pattern.orEmpty(),
            deliveries = n.deliveries.map { Delivery(it.id.toString(), it.instance.toString(), it.enabled, it.overrides) },
        )

        fun blank(group: String, zone: ZoneId) = NotificationForm(group = group, zone = zone.id)
    }
}
