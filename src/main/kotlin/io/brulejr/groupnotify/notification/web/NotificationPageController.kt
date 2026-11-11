/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.console.web.Htmx
import io.brulejr.groupnotify.console.web.Toast
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.content.Placeholders
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.delivery.PluginDescriptor
import io.brulejr.groupnotify.dispatch.DispatchService
import io.brulejr.groupnotify.dispatch.SendResult
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.CronSpec
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.notification.NotificationFilter
import io.brulejr.groupnotify.notification.NotificationProblem
import io.brulejr.groupnotify.notification.NotificationResult
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.Schedule
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.plugin.FieldType
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
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * The console's notification pages: the list, the editor, a notification's own page,
 * and the actions on it. Every one calls the same services as the REST API, so the same
 * group rules apply: a notification outside the user's groups is not found.
 */
@Controller
@RequestMapping("/ui/notifications")
class NotificationPageController(
    private val notifications: NotificationService,
    private val deliveries: DeliveryService,
    private val dispatch: DispatchService,
    private val content: ContentService,
    private val audit: AuditService,
    private val groups: GroupService,
    private val clock: Clock,
) {

    // ---- the list ----

    @GetMapping
    fun list(
        user: NotifyUser,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) status: NotificationStatus?,
        model: Model,
    ): String {
        rows(user, q, group, status, model)
        model.addAttribute("groups", groups.viewable(user, notifications.groupsInUse()).toMutableList())
        model.addAttribute("statuses", NotificationStatus.entries.toMutableList())
        model.addAttribute("canCreate", groups.editable(user, notifications.groupsInUse()).isNotEmpty())
        model.addAttribute("total", notifications.list(user).size)
        return "ui/notifications/list"
    }

    /** The rows alone, for the filter and after any change. */
    @GetMapping("/rows")
    fun rows(
        user: NotifyUser,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) status: NotificationStatus?,
        model: Model,
    ): String {
        val rows = notifications.list(user, NotificationFilter(q, group, status)).map { row(user, it) }
        model.addAttribute("rows", rows.toMutableList())
        model.addAttribute("q", q.orEmpty())
        model.addAttribute("group", group.orEmpty())
        model.addAttribute("status", status)
        return "ui/notifications/list :: rows"
    }

    // ---- one notification ----

    @GetMapping("/{id}")
    fun detail(user: NotifyUser, @PathVariable id: UUID, @RequestParam(required = false) saved: String?, model: Model): String {
        detailModel(user, find(id, user), model)
        when (saved) {
            "created" -> model.addAttribute("toast", Toast.success("Created"))
            "updated" -> model.addAttribute("toast", Toast.success("Saved"))
        }
        return "ui/notifications/detail"
    }

    /** The body of the page, reloaded after an action on it. */
    @GetMapping("/{id}/body")
    fun detailBody(user: NotifyUser, @PathVariable id: UUID, model: Model): String {
        detailModel(user, find(id, user), model)
        return "ui/notifications/detail :: body"
    }

    // ---- the editor ----

    @GetMapping("/new")
    fun create(user: NotifyUser, @RequestParam(required = false) group: String?, model: Model): String {
        val editable = groups.editable(user, notifications.groupsInUse())
        if (editable.isEmpty()) throw ResponseStatusException(HttpStatus.FORBIDDEN, "You may not create notifications")
        val form = NotificationForm.blank(group?.takeIf { it in editable } ?: editable.singleOrNull().orEmpty(), notifications.defaultZone)
        formModel(user, null, form, emptyList(), model)
        return "ui/notifications/form"
    }

    @GetMapping("/{id}/edit")
    fun edit(user: NotifyUser, @PathVariable id: UUID, model: Model): String {
        val notification = find(id, user)
        if (!notifications.canEdit(user, notification)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "You may not change this notification")
        formModel(user, notification, NotificationForm.of(notification, deliveries::mask), emptyList(), model)
        return "ui/notifications/form"
    }

    /** A copy to start a new one from: everything but the secrets, which are not copied. */
    @GetMapping("/{id}/copy")
    fun copy(user: NotifyUser, @PathVariable id: UUID, model: Model): String {
        val original = find(id, user)
        val form = NotificationForm.of(original) { plugin, target -> deliveries.mask(plugin, target).filterValues { it != DeliveryService.MASK } }
        formModel(user, null, form.copy(title = "${form.title} (copy)", deliveries = form.deliveries.map { it.copy(id = "") }), emptyList(), model)
        return "ui/notifications/form"
    }

    @PostMapping
    fun save(user: NotifyUser, @RequestParam params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String =
        submit(user, null, params, model, response)

    @PostMapping("/{id}")
    fun update(user: NotifyUser, @PathVariable id: UUID, @RequestParam params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String =
        submit(user, find(id, user), params, model, response)

    /** A new delivery row for [plugin], numbered [index]. */
    @GetMapping("/delivery")
    fun deliveryRow(@RequestParam plugin: String, @RequestParam index: Int, model: Model): String {
        val descriptor = deliveries.plugin(plugin) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No plugin $plugin")
        model.addAttribute("row", deliveryRow(index, NotificationForm.Delivery(plugin = plugin, values = defaults(descriptor)), descriptor, emptyMap()))
        return "ui/notifications/parts :: delivery"
    }

    /** The next few runs of a schedule as typed, or why it has none. */
    @GetMapping("/schedule-preview")
    fun schedulePreview(
        @RequestParam(defaultValue = "ONCE") kind: ScheduleKind,
        @RequestParam(defaultValue = "") runAt: String,
        @RequestParam(defaultValue = "") cron: String,
        @RequestParam(defaultValue = "") zone: String,
        @RequestParam(defaultValue = "") until: String,
        model: Model,
    ): String {
        val zoneId = runCatching { ZoneId.of(zone.ifBlank { notifications.defaultZone.id }) }.getOrNull()
        val (runs, problem) = when {
            zoneId == null -> emptyList<java.time.Instant>() to "Choose a time zone."
            kind == ScheduleKind.ONCE -> {
                val at = runCatching { LocalDateTime.parse(runAt).atZone(zoneId).toInstant() }.getOrNull()
                when {
                    at == null -> emptyList<java.time.Instant>() to "Choose a date and time."
                    at <= clock.instant() -> listOf(at) to "That time has passed."
                    else -> listOf(at) to null
                }
            }
            cron.isBlank() -> emptyList<java.time.Instant>() to "Enter a cron expression, or pick an example."
            else -> CronSpec.problem(cron)?.let { emptyList<java.time.Instant>() to "It $it." } ?: run {
                val end = runCatching { LocalDate.parse(until).atTime(LocalTime.MAX).atZone(zoneId).toInstant() }.getOrNull()
                val upcoming = Schedule(ScheduleKind.RECURRING, zoneId, cron = cron, until = end).upcoming(clock.instant(), 5)
                upcoming to if (upcoming.isEmpty()) "It never runs before the end date." else null
            }
        }
        model.addAttribute("runs", runs.toMutableList())
        model.addAttribute("problem", problem)
        model.addAttribute("zone", zoneId ?: notifications.defaultZone)
        return "ui/notifications/parts :: schedulePreview"
    }

    // ---- actions ----

    @PostMapping("/{id}/pause")
    fun pause(user: NotifyUser, @PathVariable id: UUID, model: Model, response: HttpServletResponse) =
        act(notifications.pause(id, user), "Paused", model, response)

    @PostMapping("/{id}/resume")
    fun resume(user: NotifyUser, @PathVariable id: UUID, model: Model, response: HttpServletResponse) =
        act(notifications.resume(id, user), "Resumed", model, response)

    @PostMapping("/{id}/send")
    fun send(user: NotifyUser, @PathVariable id: UUID, @RequestParam(required = false) deliveryId: UUID?, model: Model, response: HttpServletResponse): String {
        val toast = when (val result = dispatch.sendNow(id, user, deliveryId)) {
            is SendResult.Sent -> when {
                result.failed == 0 -> Toast.success(
                    if (result.reports.size == 1) "Sent via ${result.reports.single().pluginName}" else "Sent through ${result.reports.size} deliveries",
                )
                result.failed == result.reports.size -> Toast.danger("Not sent: " + result.reports.joinToString("; ") { "${it.pluginName}: ${it.outcome.detail}" })
                else -> Toast.warning(
                    "Sent through ${result.reports.size - result.failed} of ${result.reports.size}; " +
                        result.reports.filter { !it.outcome.delivered }.joinToString("; ") { "${it.pluginName}: ${it.outcome.detail}" },
                )
            }
            SendResult.NotFound -> Toast.danger("No such notification")
            SendResult.Forbidden -> Toast.danger("You may not send this notification")
            SendResult.NothingToSend -> Toast.warning("It has no enabled delivery")
        }
        model.addAttribute("toast", toast)
        Htmx.trigger(response, Htmx.NOTIFICATIONS_CHANGED)
        return "ui/layout :: toastOnly"
    }

    @DeleteMapping("/{id}")
    fun delete(
        user: NotifyUser,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "false") redirect: Boolean,
        model: Model,
        response: HttpServletResponse,
    ): String {
        val result = notifications.delete(id, user)
        if (result is NotificationResult.Saved && redirect) {
            Htmx.redirect(response, "/ui/notifications")
            return "ui/layout :: toastOnly"
        }
        return act(result, "Deleted", model, response)
    }

    // ---- shared ----

    private fun submit(user: NotifyUser, existing: Notification?, params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String {
        val form = NotificationForm.from(params)
        val (draft, parseProblems) = form.toDraft()
        val result = when {
            parseProblems.isNotEmpty() -> NotificationResult.Invalid(parseProblems)
            existing == null -> notifications.create(draft, user)
            else -> notifications.update(existing.id, draft, user)
        }
        return when (result) {
            is NotificationResult.Saved -> {
                Htmx.redirect(response, "/ui/notifications/${result.notification.id}?saved=${if (existing == null) "created" else "updated"}")
                "ui/layout :: toastOnly"
            }
            is NotificationResult.Invalid -> {
                formModel(user, existing, form, result.problems, model)
                model.addAttribute("toast", Toast.danger("Not saved: ${result.problems.size} problem${if (result.problems.size == 1) "" else "s"} to fix"))
                "ui/notifications/form :: form"
            }
            NotificationResult.NotFound -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
            NotificationResult.Forbidden -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "You may not change this notification")
        }
    }

    private fun act(result: NotificationResult, done: String, model: Model, response: HttpServletResponse): String {
        model.addAttribute(
            "toast",
            when (result) {
                is NotificationResult.Saved -> Toast.success("$done ${result.notification.title}")
                is NotificationResult.Invalid -> Toast.danger(result.problems.joinToString("; ") { it.reason.replaceFirstChar(Char::uppercase) })
                NotificationResult.NotFound -> Toast.danger("No such notification")
                NotificationResult.Forbidden -> Toast.danger("You may not change this notification")
            },
        )
        Htmx.trigger(response, Htmx.NOTIFICATIONS_CHANGED)
        return "ui/layout :: toastOnly"
    }

    private fun find(id: UUID, user: NotifyUser): Notification =
        notifications.find(id, user) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such notification")

    private fun row(user: NotifyUser, n: Notification) =
        NotificationRow(n, groups.displayName(n.group), notifications.canEdit(user, n), badges(n).toMutableList())

    private fun badges(n: Notification) = n.deliveries.map { d ->
        val plugin = deliveries.plugin(d.plugin)
        DeliveryBadge(
            d.id, d.plugin, plugin?.name ?: d.plugin, plugin?.icon ?: "question-circle", d.label,
            deliveries.summarize(d.plugin, d.target), d.enabled, plugin?.ready ?: false, plugin?.reason ?: "not installed",
        )
    }

    private fun detailModel(user: NotifyUser, n: Notification, model: Model) {
        val now = clock.instant()
        val groupName = groups.displayName(n.group)
        val message = content.render(n, groupName, n.nextRunAt ?: now)
        model.addAttribute("n", n)
        model.addAttribute("groupName", groupName)
        model.addAttribute("canEdit", notifications.canEdit(user, n))
        model.addAttribute("message", message)
        model.addAttribute("deliveries", badges(n).toMutableList())
        model.addAttribute("upcoming", (if (n.status == NotificationStatus.ACTIVE) n.schedule.upcoming(now, 5) else emptyList()).toMutableList())
        model.addAttribute("cronMeaning", n.schedule.cron?.let { CronSpec.EXAMPLES[it.trim()] })
        model.addAttribute("history", audit.history(user, AuditQuery(notificationId = n.id, limit = 25)).toMutableList())
    }

    private fun formModel(user: NotifyUser, existing: Notification?, form: NotificationForm, problems: List<NotificationProblem>, model: Model) {
        val errors = problems.associate { it.field to it.reason }
        val plugins = deliveries.plugins()
        val pluginsById = plugins.associateBy { it.id }
        val storedSecrets = existing?.deliveries.orEmpty().associate { it.id.toString() to it.target }
        val rows = form.deliveries.mapIndexedNotNull { index, delivery ->
            pluginsById[delivery.plugin]?.let { plugin ->
                deliveryRow(index, delivery, plugin, errors, storedSecrets[delivery.id].orEmpty())
            }
        }
        val editable = groups.editable(user, notifications.groupsInUse()).toMutableList()
        if (form.group.isNotBlank() && form.group !in editable) editable += form.group
        model.addAttribute("existing", existing)
        model.addAttribute("form", form)
        model.addAttribute("errors", HashMap(errors))
        model.addAttribute("deliveryErrors", errors.filterKeys { it == "deliveries" }.values.toMutableList())
        model.addAttribute("rows", rows.toMutableList())
        model.addAttribute("plugins", plugins.toMutableList())
        model.addAttribute("editableGroups", editable)
        model.addAttribute("formats", BodyFormat.entries.toMutableList())
        model.addAttribute("kinds", ScheduleKind.entries.toMutableList())
        model.addAttribute("zones", ZONES.toMutableList())
        model.addAttribute("cronExamples", LinkedHashMap(CronSpec.EXAMPLES))
        model.addAttribute("placeholders", LinkedHashMap(Placeholders.NAMES))
        model.addAttribute("isAdmin", user.admin)
    }

    private fun deliveryRow(
        index: Int,
        delivery: NotificationForm.Delivery,
        plugin: PluginDescriptor,
        errors: Map<String, String>,
        stored: Map<String, String> = emptyMap(),
    ): DeliveryRow {
        val inputs = plugin.fields.map { field ->
            val value = delivery.values[field.key].orEmpty()
            val saved = field.secret && (value == DeliveryService.MASK || (value.isBlank() && !stored[field.key].isNullOrBlank()))
            FieldInput(
                field = field,
                name = "deliveries[$index].target.${field.key}",
                inputId = "d$index-${field.key}",
                value = if (field.secret) "" else value,
                saved = saved,
                error = errors["deliveries[$index].target.${field.key}"],
            )
        }
        return DeliveryRow(index, delivery.id, plugin, delivery.label, delivery.enabled, inputs.toMutableList(), errors["deliveries[$index].plugin"])
    }

    private fun defaults(plugin: PluginDescriptor) = plugin.fields.filter { it.defaultValue.isNotEmpty() || it.type == FieldType.CHECKBOX }
        .associate { it.key to it.defaultValue }

    private companion object {
        /** The zones offered first; any IANA zone can be typed. */
        val ZONES = listOf(
            "America/New_York", "America/Chicago", "America/Denver", "America/Phoenix", "America/Los_Angeles",
            "America/Anchorage", "Pacific/Honolulu", "America/Halifax", "Europe/London", "Europe/Berlin", "UTC",
        )
    }
}
