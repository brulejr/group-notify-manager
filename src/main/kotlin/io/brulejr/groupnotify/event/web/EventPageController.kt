/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.web

import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.console.web.Htmx
import io.brulejr.groupnotify.console.web.Toast
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.content.Placeholders
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.dispatch.DispatchService
import io.brulejr.groupnotify.dispatch.SendResult
import io.brulejr.groupnotify.event.Event
import io.brulejr.groupnotify.event.EventResult
import io.brulejr.groupnotify.event.EventService
import io.brulejr.groupnotify.event.ReminderTiming
import io.brulejr.groupnotify.event.ReminderUnit
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.CronSpec
import io.brulejr.groupnotify.notification.NotificationProblem
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.notification.PatternParser
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.notification.web.DeliveryRows
import io.brulejr.groupnotify.notification.web.NotificationForm
import io.brulejr.groupnotify.notification.web.NotificationPageController
import io.brulejr.groupnotify.security.NotifyUser
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * The console's event pages: the list, the editor, an event's own page with its coming
 * occurrences and their notes, and the actions on it. They call the same services as the
 * REST API, so a group's members manage its events and nobody else sees them.
 */
@Controller
@RequestMapping("/ui/events")
class EventPageController(
    private val events: EventService,
    private val notifications: NotificationService,
    private val dispatch: DispatchService,
    private val audit: AuditService,
    private val groups: GroupService,
    deliveries: DeliveryService,
    instances: InstanceService,
    private val clock: java.time.Clock,
    private val content: ContentService,
) {

    private val rowsHelper = DeliveryRows(deliveries, instances, groups)

    @GetMapping
    fun list(user: NotifyUser, @RequestParam(required = false) group: String?, model: Model): String {
        val rows = events.list(user, group).map { e ->
            EventRow(
                event = e,
                groupName = groups.displayName(e.group),
                canEdit = events.canEdit(user, e),
                // Where any of its reminders goes, each instance once.
                deliveries = rowsHelper.badges(e.allDeliveries.distinctBy { it.instance }).toMutableList(),
                nextOccurrence = events.occurrences(e, 1).firstOrNull(),
                nextReminder = e.next?.let { e.reminder(it.reminder)?.describe() },
            )
        }
        model.addAttribute("rows", rows.toMutableList())
        model.addAttribute("groups", groups.viewable(user, inUse()).toMutableList())
        model.addAttribute("group", group.orEmpty())
        return "ui/events/list"
    }

    @GetMapping("/{id}")
    fun detail(user: NotifyUser, @PathVariable id: UUID, @RequestParam(required = false) saved: String?, model: Model): String {
        detailModel(user, find(id, user), model)
        when (saved) {
            "created" -> model.addAttribute("toast", Toast.success("Created"))
            "updated" -> model.addAttribute("toast", Toast.success("Saved"))
            "note" -> model.addAttribute("toast", Toast.success("Note saved"))
            "noteRemoved" -> model.addAttribute("toast", Toast.success("Note removed"))
        }
        return "ui/events/detail"
    }

    /** The body of the page, reloaded after an action on it. */
    @GetMapping("/{id}/body")
    fun detailBody(user: NotifyUser, @PathVariable id: UUID, model: Model): String {
        detailModel(user, find(id, user), model)
        return "ui/events/detail :: body"
    }

    // ---- the editor ----

    @GetMapping("/new")
    fun create(user: NotifyUser, @RequestParam(required = false) group: String?, model: Model): String {
        val editable = groups.editable(user, inUse())
        if (editable.isEmpty()) throw ResponseStatusException(HttpStatus.FORBIDDEN, "You may not create events")
        val form = EventForm.blank(group?.takeIf { it in editable } ?: editable.singleOrNull().orEmpty(), notifications.defaultZone.id)
        formModel(user, null, form, emptyList(), model)
        return "ui/events/form"
    }

    @GetMapping("/{id}/edit")
    fun edit(user: NotifyUser, @PathVariable id: UUID, model: Model): String {
        val event = find(id, user)
        formModel(user, event, EventForm.of(event), emptyList(), model)
        return "ui/events/form"
    }

    @PostMapping
    fun save(user: NotifyUser, @RequestParam params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String =
        submit(user, null, params, model, response)

    @PostMapping("/{id}")
    fun update(user: NotifyUser, @PathVariable id: UUID, @RequestParam params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String =
        submit(user, find(id, user), params, model, response)

    /** A new reminder row numbered [index], offering [group]'s instances for its deliveries. */
    @GetMapping("/reminder")
    fun reminderRow(user: NotifyUser, @RequestParam index: Int, @RequestParam(defaultValue = "") group: String, model: Model): String {
        model.addAttribute("r", reminderRow(index, EventForm.Reminder(), emptyMap()))
        model.addAttribute("units", ReminderUnit.entries.toMutableList())
        model.addAttribute("timings", ReminderTiming.entries.toMutableList())
        rowsHelper.menu(user, group, model)
        return "ui/events/parts :: reminder"
    }

    /** A new property row numbered [index]. */
    @GetMapping("/property")
    fun propertyRow(@RequestParam index: Int, model: Model): String {
        model.addAttribute("p", PropertyRow(index, "", "", null, null))
        return "ui/events/parts :: property"
    }

    // ---- actions ----

    /**
     * One occurrence's note, on a page of its own: the same editor as the body, Markdown or
     * rich text, which is too heavy to repeat for every occurrence on the event's page.
     */
    @GetMapping("/{id}/notes/{key}")
    fun notePage(user: NotifyUser, @PathVariable id: UUID, @PathVariable key: Long, model: Model): String {
        val event = find(id, user)
        val occurrence = Instant.ofEpochSecond(key)
        if (!events.isOccurrence(event, occurrence)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such occurrence")
        if (!events.canEdit(user, event)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "You may not change this event")
        val note = event.notes[occurrence]
        noteModel(event, occurrence, NotificationForm(body = note?.text.orEmpty(), format = note?.format ?: BodyFormat.MARKDOWN), emptyMap(), model)
        return "ui/events/note"
    }

    /** Saves the note of one occurrence, or with an empty body removes it, and goes back to the event. */
    @PostMapping("/{id}/notes/{key}")
    fun saveNote(
        user: NotifyUser,
        @PathVariable id: UUID,
        @PathVariable key: Long,
        @RequestParam(defaultValue = "") body: String,
        @RequestParam(defaultValue = "MARKDOWN") format: BodyFormat,
        model: Model,
        response: HttpServletResponse,
    ): String {
        val event = find(id, user)
        val occurrence = Instant.ofEpochSecond(key)
        return when (val result = events.setNote(id, occurrence, body, format, user)) {
            is EventResult.Saved -> {
                Htmx.redirect(response, "/ui/events/$id?saved=${if (occurrence in result.event.notes) "note" else "noteRemoved"}")
                "ui/layout :: toastOnly"
            }
            is EventResult.Invalid -> {
                noteModel(event, occurrence, NotificationForm(body = body, format = format), result.problems.associate { it.field to it.reason }, model)
                model.addAttribute("toast", Toast.danger(result.problems.joinToString("; ") { it.reason.replaceFirstChar(Char::uppercase) }))
                "ui/events/note :: form"
            }
            EventResult.NotFound -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
    }

    @PostMapping("/{id}/pause")
    fun pause(user: NotifyUser, @PathVariable id: UUID, model: Model, response: HttpServletResponse) =
        act(events.pause(id, user), "Paused", model, response)

    @PostMapping("/{id}/resume")
    fun resume(user: NotifyUser, @PathVariable id: UUID, model: Model, response: HttpServletResponse) =
        act(events.resume(id, user), "Resumed", model, response)

    /** Sends one reminder now, for the next occurrence, through its deliveries or one of them. */
    @PostMapping("/{id}/send")
    fun send(
        user: NotifyUser,
        @PathVariable id: UUID,
        @RequestParam reminderId: UUID,
        @RequestParam(required = false) deliveryId: UUID?,
        model: Model,
        response: HttpServletResponse,
    ): String {
        val toast = when (val result = dispatch.sendEventNow(id, user, reminderId, deliveryId)) {
            is SendResult.Sent -> when {
                result.failed == 0 -> Toast.success(if (result.reports.size == 1) "Sent via ${result.reports.single().name}" else "Sent through ${result.reports.size} deliveries")
                result.failed == result.reports.size -> Toast.danger("Not sent: " + result.reports.joinToString("; ") { "${it.name}: ${it.outcome.detail}" })
                else -> Toast.warning(
                    "Sent through ${result.reports.size - result.failed} of ${result.reports.size}; " +
                        result.reports.filter { !it.outcome.delivered }.joinToString("; ") { "${it.name}: ${it.outcome.detail}" },
                )
            }
            SendResult.NotFound -> Toast.danger("No such event or reminder")
            SendResult.Forbidden -> Toast.danger("You may not send this event")
            SendResult.NothingToSend -> Toast.warning("This reminder has no enabled delivery, or the event no occurrence to come")
        }
        model.addAttribute("toast", toast)
        Htmx.trigger(response, Htmx.EVENTS_CHANGED)
        return "ui/layout :: toastOnly"
    }

    @DeleteMapping("/{id}")
    fun delete(user: NotifyUser, @PathVariable id: UUID, model: Model, response: HttpServletResponse): String {
        val result = events.delete(id, user)
        if (result is EventResult.Saved) {
            Htmx.redirect(response, "/ui/events")
            return "ui/layout :: toastOnly"
        }
        return act(result, "Deleted", model, response)
    }

    // ---- shared ----

    private fun submit(user: NotifyUser, existing: Event?, params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String {
        val form = EventForm.from(params)
        val (draft, parseProblems) = form.toDraft()
        val result = when {
            parseProblems.isNotEmpty() -> EventResult.Invalid(parseProblems)
            existing == null -> events.create(draft, user)
            else -> events.update(existing.id, draft, user)
        }
        return when (result) {
            is EventResult.Saved -> {
                Htmx.redirect(response, "/ui/events/${result.event.id}?saved=${if (existing == null) "created" else "updated"}")
                "ui/layout :: toastOnly"
            }
            is EventResult.Invalid -> {
                formModel(user, existing, form, result.problems, model)
                model.addAttribute("toast", Toast.danger("Not saved: ${result.problems.size} problem${if (result.problems.size == 1) "" else "s"} to fix"))
                "ui/events/form :: form"
            }
            EventResult.NotFound -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
    }

    private fun act(result: EventResult, done: String, model: Model, response: HttpServletResponse): String {
        model.addAttribute(
            "toast",
            when (result) {
                is EventResult.Saved -> Toast.success("$done: ${result.event.title}")
                is EventResult.Invalid -> Toast.danger(result.problems.joinToString("; ") { it.reason.replaceFirstChar(Char::uppercase) })
                EventResult.NotFound -> Toast.danger("No such event")
            },
        )
        Htmx.trigger(response, Htmx.EVENTS_CHANGED)
        return "ui/layout :: toastOnly"
    }

    private fun find(id: UUID, user: NotifyUser): Event = events.find(id, user) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such event")

    /** Groups that hold notifications or events: an admin may choose among them. */
    private fun inUse() = notifications.groupsInUse() + events.groupsInUse()

    private fun detailModel(user: NotifyUser, e: Event, model: Model) {
        val zone = e.schedule.zone
        val upcoming = events.upcoming(e, 50)
        val occurrences = events.occurrences(e, 5).map { occurrence ->
            OccurrenceRow(
                occurrence = occurrence,
                key = occurrence.epochSecond,
                noteHtml = e.notes[occurrence]?.let { content.html(it.text, it.format) }.orEmpty(),
                reminders = upcoming.filter { it.occurrence == occurrence }
                    .map { "${e.reminder(it.reminder)?.describe() ?: "reminder"}: ${WHEN.format(it.sendAt.atZone(zone))}" }
                    .toMutableList(),
            )
        }
        val next = occurrences.firstOrNull()?.occurrence ?: e.schedule.runAt
        model.addAttribute("e", e)
        model.addAttribute("groupName", groups.displayName(e.group))
        model.addAttribute("canEdit", events.canEdit(user, e))
        model.addAttribute("message", next?.let { events.message(e, it, null, groups.displayName(e.group), clock.instant()) })
        model.addAttribute("reminders", e.reminders.map { ReminderDetail(it, rowsHelper.badges(it.deliveries).toMutableList()) }.toMutableList())
        model.addAttribute("occurrences", occurrences.toMutableList())
        model.addAttribute("nextReminder", e.next?.let { "${e.reminder(it.reminder)?.describe() ?: "reminder"} — ${WHEN.format(it.sendAt.atZone(zone))}" })
        model.addAttribute("patternMeaning", e.schedule.parsedPattern?.describe())
        model.addAttribute("history", audit.history(user, AuditQuery(eventId = e.id, limit = 25)).toMutableList())
    }

    private fun noteModel(e: Event, occurrence: Instant, form: NotificationForm, errors: Map<String, String>, model: Model) {
        model.addAttribute("e", e)
        model.addAttribute("occurrence", occurrence)
        model.addAttribute("key", occurrence.epochSecond)
        model.addAttribute("hasNote", occurrence in e.notes)
        model.addAttribute("form", form)
        model.addAttribute("errors", HashMap(errors))
        model.addAttribute("formats", BodyFormat.entries.toMutableList())
        model.addAttribute("reminders", e.reminders.filter { it.enabled }.map { "${it.describe()}: ${WHEN.format(it.sendAt(occurrence, e.schedule.zone).atZone(e.schedule.zone))}" }.toMutableList())
        model.addAttribute("usesPlaceholder", Placeholders.uses(e.body, "occurrence.note"))
    }

    private fun formModel(user: NotifyUser, existing: Event?, form: EventForm, problems: List<NotificationProblem>, model: Model) {
        val errors = problems.associate { it.field to it.reason }
        val base = form.base
        val editable = groups.editable(user, inUse()).toMutableList()
        if (base.group.isNotBlank() && base.group !in editable) editable += base.group
        model.addAttribute("existing", existing)
        // The parts shared with the notification form read "form".
        model.addAttribute("form", base)
        model.addAttribute("errors", HashMap(errors))
        model.addAttribute("reminderErrors", errors.filterKeys { it == "reminders" }.values.toMutableList())
        model.addAttribute("propertyErrors", errors.filterKeys { it == "properties" }.values.toMutableList())
        // Each reminder's own menu reads these instances; its list and prefix are set where it is drawn.
        rowsHelper.menu(user, base.group, model)
        model.addAttribute("propertyRows", form.properties.mapIndexed { i, p -> PropertyRow(i, p.name, p.value, errors["properties[$i].name"], errors["properties[$i].value"]) }.toMutableList())
        model.addAttribute("reminderRows", form.reminders.mapIndexed { i, r -> reminderRow(i, r, errors) }.toMutableList())
        model.addAttribute("units", ReminderUnit.entries.toMutableList())
        model.addAttribute("timings", ReminderTiming.entries.toMutableList())
        model.addAttribute("editableGroups", editable)
        model.addAttribute("formats", BodyFormat.entries.toMutableList())
        model.addAttribute("kinds", ScheduleKind.entries.toMutableList())
        model.addAttribute("zones", NotificationPageController.ZONES.toMutableList())
        model.addAttribute("cronExamples", LinkedHashMap(CronSpec.EXAMPLES))
        model.addAttribute("patternExamples", LinkedHashMap(PatternParser.EXAMPLES))
        model.addAttribute("placeholders", LinkedHashMap(Placeholders.NAMES))
        model.addAttribute("eventPlaceholders", LinkedHashMap(Placeholders.EVENT_NAMES))
    }

    private fun reminderRow(index: Int, r: EventForm.Reminder, errors: Map<String, String>) = ReminderRow(
        index, r.id, r.amount, r.unit, r.timing, r.subject, r.body, r.enabled,
        errors["reminders[$index].amount"], errors["reminders[$index].subject"], errors["reminders[$index].body"],
        deliveries = r.deliveries.mapIndexed { i, d -> rowsHelper.row(i, d, errors, "reminders[$index].deliveries") }.toMutableList(),
        deliveriesError = errors["reminders[$index].deliveries"],
    )

    private companion object {
        val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a z", Locale.US)
    }
}
