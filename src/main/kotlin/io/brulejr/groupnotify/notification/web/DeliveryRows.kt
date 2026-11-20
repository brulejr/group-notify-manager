/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.delivery.web.FieldInput
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.notification.NotificationDelivery
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.ui.Model
import java.util.UUID

/**
 * The deliveries part of a form, a notification's or an event's: the Add delivery menu of
 * the group's instances, and a row per delivery with its overridable fields.
 */
class DeliveryRows(
    private val deliveries: DeliveryService,
    private val instances: InstanceService,
    private val groups: GroupService,
) {

    /** The Add delivery menu's model: the instances of [group], if [user] may see it. */
    fun menu(user: NotifyUser, group: String, model: Model, list: String = "delivery-list", prefix: String = "deliveries") {
        model.addAttribute("menuList", list)
        model.addAttribute("menuPrefix", prefix)
        val choices = if (group.isBlank() || !groups.canView(user, group)) emptyList() else instances.forGroup(group)
        val plugins = deliveries.plugins().associateBy { it.id }
        model.addAttribute("menuGroup", group)
        model.addAttribute("instances", choices.map { InstanceChoice(it.id, it.name, plugins[it.plugin]?.name ?: it.plugin, plugins[it.plugin]?.icon ?: "send", instances.summary(it)) }.toMutableList())
        model.addAttribute("canProvision", group.isNotBlank() && instances.canManage(user, group))
    }

    /**
     * One row: the instance's name and where it sends, and an input for each overridable
     * field showing what this delivery will use, its own value or the instance's. Its
     * inputs are named under [prefix]: `deliveries[2]`, or an event reminder's
     * `reminders[1].deliveries[2]`.
     */
    fun row(index: Int, delivery: NotificationForm.Delivery, errors: Map<String, String>, prefix: String = "deliveries"): DeliveryRow {
        val at = "$prefix[$index]"
        val id = at.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
        val error = errors["$at.instance"]
        val instance: PluginInstance? = runCatching { UUID.fromString(delivery.instance) }.getOrNull()?.let(instances::get)
        val plugin = instance?.let { deliveries.plugin(it.plugin) }
        if (instance == null || plugin == null) {
            return DeliveryRow(
                index, delivery.id, delivery.instance, if (instance == null) "Deleted instance" else instance.name, plugin,
                if (instance == null) "it no longer exists: remove this delivery" else "${instance.plugin} is not installed",
                delivery.enabled, mutableListOf(), error, at, id,
            )
        }
        val effective = instances.effective(instance, delivery.overrides)
        val own = instances.overrides(instance, delivery.overrides)
        val inputs = plugin.fields.filter { it.overridable }.map { field ->
            FieldInput(
                field = field,
                name = "$at.overrides.${field.key}",
                inputId = "$id-${field.key}",
                value = delivery.overrides[field.key] ?: effective[field.key].orEmpty(),
                saved = false,
                error = errors["$at.overrides.${field.key}"],
                note = if (field.key in own) "Set here, not the instance's" else null,
            )
        }
        return DeliveryRow(
            index, delivery.id, instance.id.toString(), instance.name, plugin, instances.summary(instance),
            delivery.enabled, inputs.toMutableList(), error, at, id,
        )
    }

    /** Each delivery as lists and detail pages show it: its instance, plugin, summary and readiness. */
    fun badges(list: List<NotificationDelivery>) = list.map { d ->
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

    companion object {
        /** The inputs a delivery row may be named under: a notification's, or an event reminder's. */
        val PREFIX = Regex("deliveries|reminders\\[\\d{1,3}]\\.deliveries")

        /** The element a row may be added to: a notification's list, or a reminder's. */
        val LIST = Regex("delivery-list|r\\d{1,3}-deliveries")
    }
}
