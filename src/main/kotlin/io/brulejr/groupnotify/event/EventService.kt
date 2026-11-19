/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.content.Placeholders
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.DeliveryRules
import io.brulejr.groupnotify.notification.NotificationProblem
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.ScheduleRules
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.security.NotifyUser
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Events: creating, changing, pausing and deleting them, their occurrences' notes, and
 * the messages their reminders send.
 *
 * As with notifications, every method that takes a user answers only for events of that
 * user's groups, every group's for an admin; one they may not see is not found. A
 * group's members manage its events. Every change is recorded in the audit trail.
 */
class EventService(
    private val store: EventPort,
    private val groups: GroupService,
    instances: InstanceService,
    private val content: ContentService,
    private val audit: AuditService,
    private val datafill: EventDatafill,
    private val defaultZone: String,
    private val clock: Clock,
) {

    private val deliveryRules = DeliveryRules(instances)

    fun find(id: UUID, viewer: NotifyUser): Event? = store.find(id)?.takeIf { groups.canView(viewer, it.group) }

    /** The events [viewer] may see, soonest reminder first, optionally of [group] alone or matching [q]. */
    fun list(viewer: NotifyUser, group: String? = null, q: String? = null): List<Event> {
        val needle = q?.trim().orEmpty()
        return store.list(groups.visibilityFilter(viewer))
            .filter { group.isNullOrBlank() || it.group == group }
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) || it.subject.contains(needle, ignoreCase = true) }
            .sortedWith(compareBy<Event>({ it.nextRunAt == null }, { it.nextRunAt }, { it.title.lowercase() }))
    }

    fun canEdit(viewer: NotifyUser, event: Event) = groups.canEdit(viewer, event.group)

    /** Every group that has an event, whoever may see it. */
    fun groupsInUse(): Set<String> = store.groups()

    fun create(draft: EventDraft, user: NotifyUser): EventResult {
        val built = build(draft, null, user)
        if (built is EventResult.Saved) {
            store.save(built.event)
            record(built.event, AuditEventType.CREATED, user, describeNext(built.event))
        }
        return built
    }

    fun update(id: UUID, draft: EventDraft, user: NotifyUser): EventResult {
        val existing = find(id, user) ?: return EventResult.NotFound
        val built = build(draft, existing, user)
        if (built is EventResult.Saved) {
            store.save(built.event)
            record(built.event, AuditEventType.UPDATED, user, listOfNotNull(changes(existing, built.event), describeNext(built.event)).joinToString("; "))
        }
        return built
    }

    /** Holds an event: no reminder goes out until it is resumed. */
    fun pause(id: UUID, user: NotifyUser): EventResult = change(id, user) { existing ->
        if (existing.status != NotificationStatus.ACTIVE) return@change invalid("status", "only an active event can be paused")
        save(existing.copy(status = NotificationStatus.PAUSED, next = null), user, AuditEventType.PAUSED)
    }

    /** Picks a paused event up from now: reminders it missed are not sent. */
    fun resume(id: UUID, user: NotifyUser): EventResult = change(id, user) { existing ->
        if (existing.status != NotificationStatus.PAUSED) return@change invalid("status", "only a paused event can be resumed")
        val next = EventCalendar.next(existing.schedule, existing.reminders, clock.instant())
            ?: return@change invalid("schedule", "no reminder is left to send")
        save(existing.copy(status = NotificationStatus.ACTIVE, next = next), user, AuditEventType.RESUMED, describeNext(existing.copy(next = next)))
    }

    fun delete(id: UUID, user: NotifyUser): EventResult = change(id, user) { existing ->
        store.delete(id)
        record(existing, AuditEventType.DELETED, user)
        EventResult.Saved(existing)
    }

    /**
     * Sets, or with a blank [note] removes, the note of the occurrence starting at
     * [occurrence], which must be one of the event's, written in [format]. Notes of
     * occurrences long past are dropped as this is saved.
     */
    fun setNote(id: UUID, occurrence: Instant, note: String, format: BodyFormat, user: NotifyUser): EventResult = change(id, user) { existing ->
        val now = clock.instant()
        if (!isOccurrence(existing, occurrence)) return@change invalid("occurrence", "is not one of this event's occurrences")
        if (note.length > MAX_NOTE) return@change invalid("body", "must be at most $MAX_NOTE characters")
        val kept = existing.notes.filterKeys { it > now.minus(NOTE_RETENTION) }
        // Rich text with nothing in it, "<p><br></p>", is no note either.
        val empty = content.text(note, format).isBlank()
        val notes = if (empty) kept - occurrence else kept + (occurrence to OccurrenceNote(note.trim(), format))
        val changed = existing.copy(notes = notes)
        store.save(changed.copy(updatedBy = user.actor, updatedAt = now))
        record(changed, AuditEventType.UPDATED, user, if (empty) "removed the note of $occurrence" else "note for $occurrence")
        EventResult.Saved(changed)
    }

    /** Whether [occurrence] is one of [event]'s, an instant its schedule runs at. */
    fun isOccurrence(event: Event, occurrence: Instant) = event.schedule.nextAfter(occurrence.minusNanos(1)) == occurrence

    // ---- the messages ----

    /**
     * What the plugins are handed for [event]'s occurrence starting at [occurrence], as
     * [reminder] sends it at [at]; without a reminder, as sent by hand now.
     */
    fun message(event: Event, occurrence: Instant, reminder: Reminder?, groupName: String, at: Instant): NotificationMessage {
        val ownWords = reminder != null && reminder.body.isNotBlank()
        val subject = reminder?.subject?.takeIf { it.isNotBlank() } ?: event.subject
        val body = if (ownWords) reminder!!.body else event.body
        val format = if (ownWords) BodyFormat.MARKDOWN else event.format
        val note = event.notes[occurrence]
        // In the subject, the note's words alone; in the body, the note in the body's format,
        // where the body places it, or after the body when it does not.
        val values = placeholders(event, occurrence, reminder, at) +
            ("occurrence.note" to note?.let { content.text(it.text, it.format).replace(Regex("\\s+"), " ").trim() }.orEmpty())
        val composed = note?.let { compose(body, format, it) } ?: Placeholders.put(body, "occurrence.note", "")
        return content.render(
            id = event.id.toString(), title = event.title, group = event.group, groupName = groupName,
            subject = subject, body = composed, format = format, zone = event.schedule.zone, at = at,
            extra = values,
        )
    }

    /** [body] with [note], converted to [format], where the body places it or else after it. */
    private fun compose(body: String, format: BodyFormat, note: OccurrenceNote): String {
        val converted = content.convert(note.text, note.format, format).trim()
        if (Placeholders.uses(body, "occurrence.note")) return Placeholders.put(body, "occurrence.note", converted)
        return if (format == BodyFormat.HTML) body + converted else body.trimEnd() + "\n\n" + converted
    }

    /** The event's own placeholders for one occurrence: its time, its properties, and how far off it is. */
    fun placeholders(event: Event, occurrence: Instant, reminder: Reminder?, at: Instant): Map<String, String> =
        content.timeValues("event.", event.schedule.zone, occurrence) +
            event.properties.associate { "event.${it.name}" to it.value } +
            ("event.when" to (reminder?.relative() ?: whenFrom(at, occurrence)))

    // ---- the scheduler's ----

    /** Active events with a reminder due at [now], soonest first, whoever's they are. */
    fun due(now: Instant, limit: Int): List<Event> = store.due(now, limit)

    /**
     * Moves [event] on from its due reminder to the next, or, when it was missed by more
     * than [grace], to the next after [now], so a long outage does not send a backlog.
     */
    fun advance(event: Event, now: Instant, grace: Duration): Pair<Boolean, PendingReminder?> {
        val due = checkNotNull(event.next) { "only a due event can be advanced" }
        val next = if (Duration.between(due.sendAt, now) > grace) {
            EventCalendar.next(event.schedule, event.reminders, now)
        } else {
            EventCalendar.next(event.schedule, event.reminders, now, cursor = due)
        }
        val moved = store.advance(event.id, due.sendAt, next, if (next == null) NotificationStatus.COMPLETED else NotificationStatus.ACTIVE, now)
        return moved to next
    }

    /** An event by id, whoever's it is: for the scheduler, never for a request. */
    fun get(id: UUID): Event? = store.find(id)

    /** The next [count] reminders [event] will send, from now. */
    fun upcoming(event: Event, count: Int): List<PendingReminder> =
        if (event.status == NotificationStatus.ACTIVE) EventCalendar.upcoming(event.schedule, event.reminders, clock.instant(), count) else emptyList()

    /** The next [count] occurrences, from now, whatever its status. */
    fun occurrences(event: Event, count: Int): List<Instant> = EventCalendar.occurrences(event.schedule, clock.instant(), count)

    // ---- checking a draft ----

    private fun build(draft: EventDraft, existing: Event?, user: NotifyUser): EventResult {
        val now = clock.instant()
        val problems = mutableListOf<NotificationProblem>()
        fun problem(field: String, reason: String) = problems.add(NotificationProblem(field, reason))

        val group = draft.group.trim()
        groups.problemWith(user, group)?.let { problem("group", it) }
        val title = draft.title.trim()
        if (title.isEmpty()) problem("title", "is required") else if (title.length > 200) problem("title", "must be at most 200 characters")
        val subject = draft.subject.trim()
        if (subject.isEmpty()) problem("subject", "is required") else if (subject.length > 300) problem("subject", "must be at most 300 characters")
        if (draft.body.isBlank()) problem("body", "is required") else if (draft.body.length > datafill.maxBodyLength) problem("body", "must be at most ${datafill.maxBodyLength} characters")

        val sentRunAt = existing?.takeIf { it.status == NotificationStatus.COMPLETED }?.schedule?.runAt
        val schedule = ScheduleRules.build(draft.schedule, defaultZone, now, sentRunAt, ::problem)
        val properties = properties(draft.properties, ::problem)
        val reminders = reminders(draft.reminders, existing, ::problem)
        val deliveries = deliveryRules.build(draft.deliveries, group, existing?.deliveries.orEmpty(), datafill.maxDeliveries, ::problem)

        if (problems.isNotEmpty() || schedule == null) return EventResult.Invalid(problems.toList())

        val next = EventCalendar.next(schedule, reminders, now)
        if (next == null && existing == null) return EventResult.Invalid(listOf(NotificationProblem("reminders", "none of them is still to come: check the date and the reminders")))
        val status = when {
            existing == null -> if (draft.paused) NotificationStatus.PAUSED else NotificationStatus.ACTIVE
            existing.status == NotificationStatus.PAUSED -> NotificationStatus.PAUSED
            next == null -> NotificationStatus.COMPLETED
            else -> NotificationStatus.ACTIVE
        }
        return EventResult.Saved(
            Event(
                id = existing?.id ?: UUID.randomUUID(),
                group = group,
                title = title,
                subject = subject,
                body = draft.body,
                format = draft.format,
                schedule = schedule,
                properties = properties,
                reminders = reminders,
                deliveries = deliveries,
                // A note belongs to an occurrence: those the new schedule no longer has are dropped.
                notes = existing?.notes.orEmpty().filterKeys { schedule.nextAfter(it.minusNanos(1)) == it },
                status = status,
                next = next.takeIf { status == NotificationStatus.ACTIVE },
                lastSentAt = existing?.lastSentAt,
                createdBy = existing?.createdBy ?: user.actor,
                createdAt = existing?.createdAt ?: now,
                updatedBy = user.actor,
                updatedAt = now,
            ),
        )
    }

    private fun properties(drafts: List<EventProperty>, problem: (String, String) -> Boolean): List<EventProperty> {
        if (drafts.size > datafill.maxProperties) problem("properties", "at most ${datafill.maxProperties} properties")
        val seen = mutableSetOf<String>()
        return drafts.mapIndexedNotNull { index, draft ->
            val name = draft.name.trim().lowercase()
            val value = draft.value.trim()
            when {
                name.isEmpty() && value.isEmpty() -> return@mapIndexedNotNull null
                !PROPERTY_NAME.matches(name) -> problem("properties[$index].name", "must be letters, digits and _, starting with a letter, e.g. location")
                name in RESERVED -> problem("properties[$index].name", "is one of the event's own placeholders: choose another name")
                !seen.add(name) -> problem("properties[$index].name", "is given twice")
                value.length > 500 -> problem("properties[$index].value", "must be at most 500 characters")
            }
            EventProperty(name, value)
        }
    }

    private fun reminders(drafts: List<EventDraft.Reminder>, existing: Event?, problem: (String, String) -> Boolean): List<Reminder> {
        if (drafts.isEmpty()) problem("reminders", "add at least one reminder")
        if (drafts.size > datafill.maxReminders) problem("reminders", "at most ${datafill.maxReminders} reminders")
        val previous = existing?.reminders.orEmpty().associateBy { it.id.toString() }
        val offsets = mutableSetOf<Triple<Int, ReminderUnit, ReminderTiming>>()
        return drafts.mapIndexedNotNull { index, draft ->
            val prefix = "reminders[$index]"
            val amount = if (draft.timing == ReminderTiming.AT_START) 0 else draft.amount.trim().toIntOrNull()
            val max = Reminder.MAX.getValue(draft.unit)
            when {
                amount == null -> problem("$prefix.amount", "must be a whole number")
                draft.timing != ReminderTiming.AT_START && amount !in 1..max -> problem("$prefix.amount", "must be 1 to $max ${draft.unit.plural}")
                !offsets.add(Triple(amount, draft.unit, draft.timing).let { if (draft.timing == ReminderTiming.AT_START) Triple(0, ReminderUnit.MINUTES, it.third) else it }) ->
                    problem("$prefix.amount", "is the same as another reminder's")
            }
            if (draft.subject.length > 300) problem("$prefix.subject", "must be at most 300 characters")
            if (draft.body.length > datafill.maxBodyLength) problem("$prefix.body", "must be at most ${datafill.maxBodyLength} characters")
            Reminder(
                id = draft.id?.let { previous[it] }?.id ?: UUID.randomUUID(),
                amount = amount ?: return@mapIndexedNotNull null,
                unit = draft.unit,
                timing = draft.timing,
                subject = draft.subject.trim(),
                body = draft.body.trim(),
                enabled = draft.enabled,
            )
        }
    }

    // ---- shared ----

    private fun change(id: UUID, user: NotifyUser, block: (Event) -> EventResult): EventResult {
        val existing = find(id, user) ?: return EventResult.NotFound
        return block(existing)
    }

    private fun save(event: Event, user: NotifyUser, type: AuditEventType, detail: String = ""): EventResult {
        val changed = event.copy(updatedBy = user.actor, updatedAt = clock.instant())
        store.save(changed)
        record(changed, type, user, detail)
        return EventResult.Saved(changed)
    }

    private fun invalid(field: String, reason: String) = EventResult.Invalid(listOf(NotificationProblem(field, reason)))

    private fun record(event: Event, type: AuditEventType, user: NotifyUser, detail: String = "") {
        audit.record(
            AuditEvent(
                occurredAt = clock.instant(),
                type = type,
                actor = user.actor,
                notificationId = null,
                notificationTitle = event.title,
                group = event.group,
                detail = detail,
                eventId = event.id,
            ),
        )
    }

    private fun describeNext(event: Event): String = event.next?.let { next ->
        val reminder = event.reminder(next.reminder)
        "next reminder ${next.sendAt}" + (reminder?.let { " (${it.describe()} ${next.occurrence})" } ?: "")
    } ?: if (event.status == NotificationStatus.PAUSED) "paused" else ""

    private fun changes(before: Event, after: Event): String? = buildList {
        if (before.group != after.group) add("group ${before.group} → ${after.group}")
        if (before.title != after.title) add("title")
        if (before.subject != after.subject) add("subject")
        if (before.body != after.body || before.format != after.format) add("body")
        if (before.schedule != after.schedule) add("schedule")
        if (before.properties != after.properties) add("properties")
        if (before.reminders.map { it.copy(id = NO_ID) } != after.reminders.map { it.copy(id = NO_ID) }) add("reminders")
        if (before.deliveries.map { it.copy(id = NO_ID) } != after.deliveries.map { it.copy(id = NO_ID) }) add("deliveries")
    }.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "changed ")

    /** How far [occurrence] is from [at], in words, for a message sent by hand. */
    private fun whenFrom(at: Instant, occurrence: Instant): String {
        val minutes = Duration.between(at, occurrence).toMinutes()
        val ahead = minutes >= 0
        val size = Math.abs(minutes)
        val words = when {
            size < 1 -> return "now"
            size < 120 -> ReminderUnit.MINUTES.words(size.toInt())
            size < 48 * 60 -> ReminderUnit.HOURS.words((size / 60).toInt())
            else -> ReminderUnit.DAYS.words((size / (24 * 60)).toInt())
        }
        return if (ahead) "in $words" else "$words ago"
    }

    private companion object {
        val NO_ID: UUID = UUID(0, 0)
        val PROPERTY_NAME = Regex("[a-z][a-z0-9_]{0,39}")
        val RESERVED = setOf("date", "time", "weekday", "datetime", "when")
        const val MAX_NOTE = 5_000
        val NOTE_RETENTION: Duration = Duration.ofDays(60)
    }
}
