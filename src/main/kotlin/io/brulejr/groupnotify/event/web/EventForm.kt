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
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.NotificationProblem
import io.brulejr.groupnotify.notification.web.NotificationForm
import org.springframework.util.MultiValueMap

/**
 * An event's form as submitted, kept as typed. Its message and schedule are a
 * notification's form, [base], whose parts the page shares; its own are the properties,
 * `properties[n].name` and `.value`, and the reminders, `reminders[n].amount`, `.unit`,
 * `.timing`, `.subject`, `.body`, `.enabled`, each with its deliveries,
 * `reminders[n].deliveries[m].instance` and so on. n and m are row numbers and may have gaps.
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
        val deliveries: List<NotificationForm.Delivery> = emptyList(),
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
            reminders = reminders.map { r ->
                EventDraft.Reminder(
                    r.id.ifBlank { null }, r.amount, r.unit, r.timing, r.subject, r.body, r.enabled,
                    r.deliveries.map { NotificationDraft.Delivery(it.id.ifBlank { null }, it.instance, it.overrides, it.enabled) },
                )
            },
            paused = base.paused,
        ) to problems
    }

    companion object {
        private val ROW = Regex("(properties|reminders)\\[(\\d+)]\\.(\\w+)")
        private val REMINDER_DELIVERY = Regex("reminders\\[(\\d+)]\\.deliveries\\[(\\d+)]\\.(.+)")

        fun from(params: MultiValueMap<String, String>): EventForm {
            val rows = mapOf("properties" to sortedMapOf<Int, MutableMap<String, String>>(), "reminders" to sortedMapOf())
            val reminderDeliveries = sortedMapOf<Int, java.util.SortedMap<Int, MutableMap<String, String>>>()
            params.forEach { (name, values) ->
                REMINDER_DELIVERY.matchEntire(name)?.let { m ->
                    reminderDeliveries.getOrPut(m.groupValues[1].toInt()) { sortedMapOf() }
                        .getOrPut(m.groupValues[2].toInt()) { mutableMapOf() }[m.groupValues[3]] = values.last()
                }
                ROW.matchEntire(name)?.let { m ->
                    // A checkbox is sent with a hidden "false" before it, so the last value wins.
                    rows.getValue(m.groupValues[1]).getOrPut(m.groupValues[2].toInt()) { mutableMapOf() }[m.groupValues[3]] = values.last()
                }
            }
            return EventForm(
                base = NotificationForm.from(params),
                properties = rows.getValue("properties").values.map { Property(it["name"].orEmpty(), it["value"].orEmpty()) },
                reminders = rows.getValue("reminders").map { (index, r) ->
                    Reminder(
                        id = r["id"].orEmpty(),
                        amount = r["amount"].orEmpty(),
                        unit = runCatching { ReminderUnit.valueOf(r["unit"].orEmpty()) }.getOrDefault(ReminderUnit.DAYS),
                        timing = runCatching { ReminderTiming.valueOf(r["timing"].orEmpty()) }.getOrDefault(ReminderTiming.BEFORE),
                        subject = r["subject"].orEmpty(),
                        body = r["body"].orEmpty(),
                        enabled = r["enabled"] != "false",
                        deliveries = reminderDeliveries[index].orEmpty().values.filter { !it["instance"].isNullOrBlank() }.map { d ->
                            NotificationForm.Delivery(
                                id = d["id"].orEmpty(),
                                instance = d.getValue("instance"),
                                enabled = d["enabled"] != "false",
                                overrides = d.filterKeys { it.startsWith("overrides.") }.mapKeys { it.key.removePrefix("overrides.") },
                            )
                        },
                    )
                },
            )
        }

        fun of(e: Event) = EventForm(
            base = NotificationForm.of(e.group, e.title, e.subject, e.body, e.format, e.schedule, emptyList()),
            properties = e.properties.map { Property(it.name, it.value) },
            reminders = e.reminders.map { r ->
                Reminder(
                    r.id.toString(), r.amount.toString(), r.unit, r.timing, r.subject, r.body, r.enabled,
                    r.deliveries.map { NotificationForm.Delivery(it.id.toString(), it.instance.toString(), it.enabled, it.overrides) },
                )
            },
        )

        /** A new event in [group]: a day before and an hour before, as most are reminded. */
        fun blank(group: String, zone: String) = EventForm(
            base = NotificationForm(group = group, zone = zone),
            properties = listOf(Property("location", "")),
            reminders = listOf(Reminder(amount = "1", unit = ReminderUnit.DAYS), Reminder(amount = "1", unit = ReminderUnit.HOURS)),
        )
    }
}
