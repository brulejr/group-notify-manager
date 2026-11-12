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
import io.brulejr.groupnotify.delivery.web.FieldInput
import io.brulejr.groupnotify.dispatch.DispatchService
import io.brulejr.groupnotify.dispatch.SendResult
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.CronSpec
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.notification.NotificationFilter
import io.brulejr.groupnotify.notification.NotificationProblem
import io.brulejr.groupnotify.notification.NotificationResult
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.PatternParser
import io.brulejr.groupnotify.notification.Schedule
import io.brulejr.groupnotify.notification.ScheduleKind
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
    private val instances: InstanceService,
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
        formModel(user, notification, NotificationForm.of(notification), emptyList(), model)
        return "ui/notifications/form"
    }

    /** A copy to start a new one from, through the same instances. */
    @GetMapping("/{id}/copy")
    fun copy(user: NotifyUser, @PathVariable id: UUID, model: Model): String {
        val original = find(id, user)
        val form = NotificationForm.of(original)
        formModel(user, null, form.copy(title = "${form.title} (copy)", deliveries = form.deliveries.map { it.copy(id = "") }), emptyList(), model)
        return "ui/notifications/form"
    }

    @PostMapping
    fun save(user: NotifyUser, @RequestParam params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String =
        submit(user, null, params, model, response)

    @PostMapping("/{id}")
    fun update(user: NotifyUser, @PathVariable id: UUID, @RequestParam params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String =
        submit(user, find(id, user), params, model, response)

    /** A new delivery row through [instance], numbered [index]; the instance must be in a group [user] may see. */
    @GetMapping("/delivery")
    fun deliveryRow(user: NotifyUser, @RequestParam instance: UUID, @RequestParam index: Int, model: Model): String {
        val chosen = instances.find(instance, user) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such delivery instance")
        model.addAttribute("row", deliveryRow(index, NotificationForm.Delivery(instance = chosen.id.toString()), emptyMap()))
        return "ui/notifications/parts :: delivery"
    }

    /** The Add delivery menu: [group]'s instances. Fetched again whenever the form's group changes. */
    @GetMapping("/instances")
    fun instanceMenu(user: NotifyUser, @RequestParam(defaultValue = "") group: String, model: Model): String {
        menuModel(user, group, model)
        return "ui/notifications/parts :: instanceMenu"
    }

    /** The next few runs of a schedule as typed, or why it has none. */
    @GetMapping("/schedule-preview")
    fun schedulePreview(
        @RequestParam(defaultValue = "ONCE") kind: ScheduleKind,
        @RequestParam(defaultValue = "") runAt: String,
        @RequestParam(defaultValue = "") cron: String,
        @RequestParam(defaultValue = "") zone: String,
        @RequestParam(defaultValue = "") until: String,
        @RequestParam(defaultValue = "") pattern: String,
        model: Model,
    ): String {
        val zoneId = runCatching { ZoneId.of(zone.ifBlank { notifications.defaultZone.id }) }.getOrNull()
        var description: String? = null
        val (runs, problem) = when {
            zoneId == null -> emptyList<java.time.Instant>() to "Choose a time zone."
            kind == ScheduleKind.PATTERN -> when {
                pattern.isBlank() -> emptyList<java.time.Instant>() to "Write a pattern, or pick an example."
                else -> runCatching { PatternParser.parse(pattern, clock.instant().atZone(zoneId).toLocalDate()) }.fold(
                    onSuccess = { parsed ->
                        description = parsed.describe()
                        val schedule = Schedule(ScheduleKind.PATTERN, zoneId, pattern = parsed.canonical())
                        val upcoming = schedule.upcoming(clock.instant(), 5)
                        upcoming to if (upcoming.isEmpty()) "It never runs before its end date." else null
                    },
                    onFailure = { emptyList<java.time.Instant>() to it.message },
                )
            }
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
        model.addAttribute("description", description)
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
                    if (result.reports.size == 1) "Sent via ${result.reports.single().name}" else "Sent through ${result.reports.size} deliveries",
                )
                result.failed == result.reports.size -> Toast.danger("Not sent: " + result.reports.joinToString("; ") { "${it.name}: ${it.outcome.detail}" })
                else -> Toast.warning(
                    "Sent through ${result.reports.size - result.failed} of ${result.reports.size}; " +
                        result.reports.filter { !it.outcome.delivered }.joinToString("; ") { "${it.name}: ${it.outcome.detail}" },
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
        val instance = instances.get(d.instance)
        val plugin = instance?.let { deliveries.plugin(it.plugin) }
        val labels = plugin?.fields.orEmpty().associate { it.key to it.label }
        DeliveryBadge(
            id = d.id,
            name = instance?.name ?: "Deleted instance",
            pluginName = plugin?.name ?: instance?.plugin ?: "unknown",
            icon = plugin?.icon ?: "question-circle",
            summary = instance?.let(instances::summary) ?: "it no longer exists",
            overridden = d.overrides.keys.map { labels[it] ?: it }.toMutableList(),
            enabled = d.enabled,
            ready = instance != null && plugin?.ready == true,
            reason = when {
                instance == null -> "its instance was deleted"
                plugin == null -> "${instance.plugin} is not installed"
                else -> plugin.reason
            },
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
        model.addAttribute("patternMeaning", n.schedule.parsedPattern?.describe())
        model.addAttribute("history", audit.history(user, AuditQuery(notificationId = n.id, limit = 25)).toMutableList())
    }

    private fun formModel(user: NotifyUser, existing: Notification?, form: NotificationForm, problems: List<NotificationProblem>, model: Model) {
        val errors = problems.associate { it.field to it.reason }
        val rows = form.deliveries.mapIndexed { index, delivery -> deliveryRow(index, delivery, errors) }
        val editable = groups.editable(user, notifications.groupsInUse()).toMutableList()
        if (form.group.isNotBlank() && form.group !in editable) editable += form.group
        model.addAttribute("existing", existing)
        model.addAttribute("form", form)
        model.addAttribute("errors", HashMap(errors))
        model.addAttribute("deliveryErrors", errors.filterKeys { it == "deliveries" }.values.toMutableList())
        model.addAttribute("rows", rows.toMutableList())
        menuModel(user, form.group, model)
        model.addAttribute("editableGroups", editable)
        model.addAttribute("formats", BodyFormat.entries.toMutableList())
        model.addAttribute("kinds", ScheduleKind.entries.toMutableList())
        model.addAttribute("zones", ZONES.toMutableList())
        model.addAttribute("cronExamples", LinkedHashMap(CronSpec.EXAMPLES))
        model.addAttribute("patternExamples", LinkedHashMap(PatternParser.EXAMPLES))
        model.addAttribute("placeholders", LinkedHashMap(Placeholders.NAMES))
        model.addAttribute("isAdmin", user.admin)
    }

    /** The Add delivery menu's model: the instances of [group], if [user] may see it. */
    private fun menuModel(user: NotifyUser, group: String, model: Model) {
        val choices = if (group.isBlank() || !groups.canView(user, group)) emptyList() else instances.forGroup(group)
        val plugins = deliveries.plugins().associateBy { it.id }
        model.addAttribute("menuGroup", group)
        model.addAttribute("instances", choices.map { InstanceChoice(it.id, it.name, plugins[it.plugin]?.name ?: it.plugin, plugins[it.plugin]?.icon ?: "send", instances.summary(it)) }.toMutableList())
        model.addAttribute("canProvision", instances.canManage(user))
    }

    /**
     * One row: the instance's name and where it sends, and an input for each overridable
     * field showing what this notification will use, its own value or the instance's.
     */
    private fun deliveryRow(index: Int, delivery: NotificationForm.Delivery, errors: Map<String, String>): DeliveryRow {
        val error = errors["deliveries[$index].instance"]
        val instance: PluginInstance? = runCatching { UUID.fromString(delivery.instance) }.getOrNull()?.let(instances::get)
        val plugin = instance?.let { deliveries.plugin(it.plugin) }
        if (instance == null || plugin == null) {
            return DeliveryRow(
                index, delivery.id, delivery.instance, if (instance == null) "Deleted instance" else instance.name, plugin,
                if (instance == null) "it no longer exists: remove this delivery" else "${instance.plugin} is not installed",
                delivery.enabled, mutableListOf(), error,
            )
        }
        val effective = instances.effective(instance, delivery.overrides)
        val own = instances.overrides(instance, delivery.overrides)
        val inputs = plugin.fields.filter { it.overridable }.map { field ->
            FieldInput(
                field = field,
                name = "deliveries[$index].overrides.${field.key}",
                inputId = "d$index-${field.key}",
                value = delivery.overrides[field.key] ?: effective[field.key].orEmpty(),
                saved = false,
                error = errors["deliveries[$index].overrides.${field.key}"],
                note = if (field.key in own) "Set for this notification" else null,
            )
        }
        return DeliveryRow(
            index, delivery.id, instance.id.toString(), instance.name, plugin, instances.summary(instance),
            delivery.enabled, inputs.toMutableList(), error,
        )
    }

    private companion object {
        /** The zones offered first; any IANA zone can be typed. */
        val ZONES = listOf(
            "America/New_York", "America/Chicago", "America/Denver", "America/Phoenix", "America/Los_Angeles",
            "America/Anchorage", "Pacific/Honolulu", "America/Halifax", "Europe/London", "Europe/Berlin", "UTC",
        )
    }
}
