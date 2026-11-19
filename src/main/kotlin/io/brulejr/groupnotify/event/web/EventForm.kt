/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.web

import io.brulejr.groupnotify.event.Event
import io.brulejr.groupnotify.event.EventDraft
import io.brulejr.groupnotify.event.EventProperty
import io.brulejr.groupnotify.event.ReminderTiming
import io.brulejr.groupnotify.event.ReminderUnit
import io.brulejr.groupnotify.notification.NotificationProblem
import io.brulejr.groupnotify.notification.web.NotificationForm
import org.springframework.util.MultiValueMap

/**
 * An event's form as submitted, kept as typed. Its message, schedule and deliveries are a
 * notification's form, [base], whose parts the page shares; its own are the properties,
 * `properties[n].name` and `.value`, and the reminders, `reminders[n].amount`, `.unit`,
 * `.timing`, `.subject`, `.body`, `.enabled`. n is a row number and may have gaps.
 */
data class EventForm(
    val base: NotificationForm,
    val properties: List<Property> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
) {
    data class Property(val name: String = "", val value: String = "")

    data class Reminder(
        val id: String = "",
        val amount: String = "1",
        val unit: ReminderUnit = ReminderUnit.DAYS,
        val timing: ReminderTiming = ReminderTiming.BEFORE,
        val subject: String = "",
        val body: String = "",
        val enabled: Boolean = true,
    )

    /** The draft, with any time that would not parse reported against its input. */
    fun toDraft(): Pair<EventDraft, List<NotificationProblem>> {
        val (base, problems) = base.toDraft()
        return EventDraft(
            group = base.group,
            title = base.title,
            subject = base.subject,
            body = base.body,
            format = base.format,
            schedule = base.schedule,
            properties = properties.map { EventProperty(it.name, it.value) },
            reminders = reminders.map { EventDraft.Reminder(it.id.ifBlank { null }, it.amount, it.unit, it.timing, it.subject, it.body, it.enabled) },
            deliveries = base.deliveries,
            paused = base.paused,
        ) to problems
    }

    companion object {
        private val ROW = Regex("(properties|reminders)\\[(\\d+)]\\.(\\w+)")

        fun from(params: MultiValueMap<String, String>): EventForm {
            val rows = mapOf("properties" to sortedMapOf<Int, MutableMap<String, String>>(), "reminders" to sortedMapOf())
            params.forEach { (name, values) ->
                ROW.matchEntire(name)?.let { m ->
                    // A checkbox is sent with a hidden "false" before it, so the last value wins.
                    rows.getValue(m.groupValues[1]).getOrPut(m.groupValues[2].toInt()) { mutableMapOf() }[m.groupValues[3]] = values.last()
                }
            }
            return EventForm(
                base = NotificationForm.from(params),
                properties = rows.getValue("properties").values.map { Property(it["name"].orEmpty(), it["value"].orEmpty()) },
                reminders = rows.getValue("reminders").values.map { r ->
                    Reminder(
                        id = r["id"].orEmpty(),
                        amount = r["amount"].orEmpty(),
                        unit = runCatching { ReminderUnit.valueOf(r["unit"].orEmpty()) }.getOrDefault(ReminderUnit.DAYS),
                        timing = runCatching { ReminderTiming.valueOf(r["timing"].orEmpty()) }.getOrDefault(ReminderTiming.BEFORE),
                        subject = r["subject"].orEmpty(),
                        body = r["body"].orEmpty(),
                        enabled = r["enabled"] != "false",
                    )
                },
            )
        }

        fun of(e: Event) = EventForm(
            base = NotificationForm.of(e.group, e.title, e.subject, e.body, e.format, e.schedule, e.deliveries),
            properties = e.properties.map { Property(it.name, it.value) },
            reminders = e.reminders.map { Reminder(it.id.toString(), it.amount.toString(), it.unit, it.timing, it.subject, it.body, it.enabled) },
        )

        /** A new event in [group]: a day before and an hour before, as most are reminded. */
        fun blank(group: String, zone: String) = EventForm(
            base = NotificationForm(group = group, zone = zone),
            properties = listOf(Property("location", "")),
            reminders = listOf(Reminder(amount = "1", unit = ReminderUnit.DAYS), Reminder(amount = "1", unit = ReminderUnit.HOURS)),
        )
    }
}
