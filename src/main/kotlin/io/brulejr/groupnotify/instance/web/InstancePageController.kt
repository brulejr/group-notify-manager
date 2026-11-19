/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.web

import io.brulejr.groupnotify.console.web.Htmx
import io.brulejr.groupnotify.console.web.Toast
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.delivery.PluginDescriptor
import io.brulejr.groupnotify.delivery.web.FieldInput
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceProblem
import io.brulejr.groupnotify.instance.InstanceResult
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.PluginInstance
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
import java.util.UUID

/**
 * The console's delivery instances: the list on the Delivery plugins page, and the form
 * one is provisioned with. Whoever may manage a group's notifications may manage its
 * instances; nobody sees a secret. The service enforces it whatever this sends.
 */
@Controller
@RequestMapping("/ui/instances")
class InstancePageController(
    private val instances: InstanceService,
    private val deliveries: DeliveryService,
    private val groups: GroupService,
) {

    /** The instances [user] may see, by group: loaded into the Delivery plugins page. */
    @GetMapping("/list")
    fun list(user: NotifyUser, model: Model): String {
        val plugins = deliveries.plugins().associateBy { it.id }
        val rows = instances.list(user).map { instance ->
            val plugin = plugins[instance.plugin]
            InstanceRow(
                instance = instance,
                groupName = groups.displayName(instance.group),
                pluginName = plugin?.name ?: instance.plugin,
                icon = plugin?.icon ?: "question-circle",
                ready = plugin?.ready == true,
                reason = plugin?.reason ?: "${instance.plugin} is not installed",
                summary = instances.summary(instance),
                usedBy = instances.usedBy(instance).toMutableList(),
                canManage = instances.canManage(user, instance.group),
            )
        }
        model.addAttribute("byGroup", LinkedHashMap(rows.groupBy { it.instance.group }.mapValues { it.value.toMutableList() }))
        model.addAttribute("manageable", HashSet(instances.manageableGroups(user, groups.displayNames().keys)))
        model.addAttribute("canManage", instances.canManageAny(user))
        model.addAttribute("ephemeralKey", instances.ephemeralKey)
        return "ui/instances/parts :: list"
    }

    @GetMapping("/new")
    fun create(user: NotifyUser, @RequestParam(required = false) plugin: String?, @RequestParam(required = false) group: String?, model: Model): String {
        if (!instances.canManageAny(user)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "You may not set up delivery instances")
        val descriptor = plugin?.let(deliveries::plugin)
        val form = InstanceForm(group = group.orEmpty(), plugin = descriptor?.id.orEmpty(), config = descriptor?.let { deliveries.defaults(it.id) }.orEmpty())
        formModel(user, null, form, emptyList(), model)
        return "ui/instances/form"
    }

    @GetMapping("/{id}/edit")
    fun edit(user: NotifyUser, @PathVariable id: UUID, model: Model): String {
        val instance = find(id, user)
        if (!instances.canManage(user, instance.group)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "You may not change this delivery instance")
        formModel(user, instance, InstanceForm.of(instance, instances.masked(instance)), emptyList(), model)
        return "ui/instances/form"
    }

    @PostMapping
    fun save(user: NotifyUser, @RequestParam params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String =
        submit(user, null, params, model, response)

    @PostMapping("/{id}")
    fun update(user: NotifyUser, @PathVariable id: UUID, @RequestParam params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String =
        submit(user, find(id, user), params, model, response)

    @DeleteMapping("/{id}")
    fun delete(user: NotifyUser, @PathVariable id: UUID, model: Model, response: HttpServletResponse): String {
        val toast = when (val result = instances.delete(id, user)) {
            is InstanceResult.Saved -> Toast.success("Deleted ${result.instance.name}")
            is InstanceResult.InUse -> Toast.danger(
                "Still used by ${result.notifications.size} notification${if (result.notifications.size == 1) "" else "s"}: " +
                    result.notifications.joinToString(", ") + ". Remove it from them first.",
            )
            InstanceResult.NotFound -> Toast.danger("No such delivery instance")
            InstanceResult.Forbidden -> Toast.danger("You may not delete this delivery instance")
            is InstanceResult.Invalid -> Toast.danger(result.problems.joinToString("; ") { it.reason })
        }
        model.addAttribute("toast", toast)
        Htmx.trigger(response, Htmx.INSTANCES_CHANGED)
        return "ui/layout :: toastOnly"
    }

    // ---- shared ----

    private fun submit(user: NotifyUser, existing: PluginInstance?, params: MultiValueMap<String, String>, model: Model, response: HttpServletResponse): String {
        val form = InstanceForm.from(params)
        val result = if (existing == null) instances.create(form.toDraft(), user) else instances.update(existing.id, form.toDraft(), user)
        return when (result) {
            is InstanceResult.Saved -> {
                Htmx.redirect(response, "/ui/plugins?saved=${result.instance.id}")
                "ui/layout :: toastOnly"
            }
            is InstanceResult.Invalid -> {
                formModel(user, existing, form, result.problems, model)
                model.addAttribute("toast", Toast.danger("Not saved: ${result.problems.size} problem${if (result.problems.size == 1) "" else "s"} to fix"))
                "ui/instances/form :: form"
            }
            InstanceResult.NotFound -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
            InstanceResult.Forbidden -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "You may not set up delivery instances")
            is InstanceResult.InUse -> throw ResponseStatusException(HttpStatus.CONFLICT)
        }
    }

    private fun formModel(user: NotifyUser, existing: PluginInstance?, form: InstanceForm, problems: List<InstanceProblem>, model: Model) {
        val errors = problems.associate { it.field to it.reason }
        val plugin = deliveries.plugin(form.plugin)
        model.addAttribute("existing", existing)
        model.addAttribute("form", form)
        model.addAttribute("errors", HashMap(errors))
        model.addAttribute("plugin", plugin)
        model.addAttribute("plugins", deliveries.plugins().toMutableList())
        model.addAttribute("inputs", plugin?.let { inputs(it, form, existing, errors) }.orEmpty().toMutableList())
        // An instance's own group stays shown when editing; a new one offers only groups the user may manage.
        val choices = instances.manageableGroups(user, groups.displayNames().keys) + listOfNotNull(existing?.group)
        model.addAttribute("knownGroups", choices.distinct().sorted().toMutableList())
    }

    private fun inputs(plugin: PluginDescriptor, form: InstanceForm, existing: PluginInstance?, errors: Map<String, String>) = plugin.fields.map { field ->
        val value = form.config[field.key].orEmpty()
        FieldInput(
            field = field,
            name = "config.${field.key}",
            inputId = "c-${field.key}",
            value = if (field.secret) "" else value,
            saved = field.secret && existing != null && !existing.config[field.key].isNullOrBlank(),
            error = errors["config.${field.key}"],
            note = if (field.overridable) "A default: a notification may change it" else null,
        )
    }

    private fun find(id: UUID, user: NotifyUser): PluginInstance =
        instances.find(id, user) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such delivery instance")
}
