/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.plugin.FieldProblem
import io.brulejr.groupnotify.security.NotifyUser
import java.time.Clock
import java.util.UUID

/**
 * Plugin instances: provisioning them, which only admins may do, and everything the rest
 * of the application needs of one, without ever handing out its secrets.
 *
 * A group's members see its instances, to choose among them when writing a
 * notification, but never their secrets: [masked] is all that leaves here. The
 * secrets are sealed by [SecretCipher] before they are stored and opened only in
 * [target], to deliver.
 *
 * Every change is recorded in the audit trail, naming the fields changed but never a
 * secret's value.
 */
class InstanceService(
    private val store: InstancePort,
    private val deliveries: DeliveryService,
    private val groups: GroupService,
    private val usage: InstanceUsage,
    private val audit: AuditService,
    private val cipher: SecretCipher,
    private val datafill: InstanceDatafill,
    private val clock: Clock,
) {

    /** Whether secrets are sealed with a key made for this run only. */
    val ephemeralKey get() = cipher.ephemeral

    /** The instances [viewer] may see, those of their groups (every group's for an admin), optionally of [group] alone. */
    fun list(viewer: NotifyUser, group: String? = null): List<PluginInstance> =
        store.list(groups.visibilityFilter(viewer)).filter { group.isNullOrBlank() || it.group == group }

    /** The instance, if [viewer] may see it. */
    fun find(id: UUID, viewer: NotifyUser): PluginInstance? = store.find(id)?.takeIf { groups.canView(viewer, it.group) }

    /** An instance by id, whoever's it is: for checking a notification and for sending, never for a request. */
    fun get(id: UUID): PluginInstance? = store.find(id)

    /** A group's instances, for the notifications of that group to choose among. */
    fun forGroup(group: String): List<PluginInstance> = store.list(setOf(group))

    fun canManage(user: NotifyUser) = user.admin

    /** The titles of the notifications that send through [instance]. */
    fun usedBy(instance: PluginInstance): List<String> = usage.notificationsUsing(instance.id)

    /** Every group that has an instance. */
    fun groupsInUse(): Set<String> = store.list(null).map { it.group }.toSortedSet()

    fun create(draft: InstanceDraft, user: NotifyUser): InstanceResult {
        if (!canManage(user)) return InstanceResult.Forbidden
        val built = build(draft, null, user)
        if (built is InstanceResult.Saved) {
            store.save(built.instance)
            record(built.instance, AuditEventType.CREATED, user, "delivery instance created")
        }
        return built
    }

    fun update(id: UUID, draft: InstanceDraft, user: NotifyUser): InstanceResult {
        val existing = find(id, user) ?: return InstanceResult.NotFound
        if (!canManage(user)) return InstanceResult.Forbidden
        val built = build(draft, existing, user)
        if (built is InstanceResult.Saved) {
            store.save(built.instance)
            changes(existing, built.instance)?.let { record(built.instance, AuditEventType.UPDATED, user, it) }
        }
        return built
    }

    /** Refused while any notification still sends through it. */
    fun delete(id: UUID, user: NotifyUser): InstanceResult {
        val existing = find(id, user) ?: return InstanceResult.NotFound
        if (!canManage(user)) return InstanceResult.Forbidden
        val using = usage.notificationsUsing(id)
        if (using.isNotEmpty()) return InstanceResult.InUse(using)
        store.delete(id)
        record(existing, AuditEventType.DELETED, user, "delivery instance deleted")
        return InstanceResult.Saved(existing)
    }

    // ---- for notifications ----

    /** The instance's configuration with every secret replaced by [DeliveryService.MASK]. */
    fun masked(instance: PluginInstance): Map<String, String> = deliveries.mask(instance.plugin, instance.config)

    /**
     * Where the instance sends, for lists and the audit trail. The plugin is given the
     * real values, as some summaries name a non-secret part of one (a webhook's id), and
     * must never repeat a secret; masked values if a secret cannot be opened.
     */
    fun summary(instance: PluginInstance): String =
        deliveries.summarize(instance.plugin, runCatching { open(instance) }.getOrElse { masked(instance) })

    /**
     * A notification's own settings for [instance]: [values] limited to the plugin's
     * overridable fields, without those equal to the instance's defaults.
     */
    fun overrides(instance: PluginInstance, values: Map<String, String>): Map<String, String> =
        deliveries.overrides(instance.plugin, values, instance.config)

    /** The instance's values with [overrides] applied and secrets masked: what a notification's form shows. */
    fun effective(instance: PluginInstance, overrides: Map<String, String>): Map<String, String> =
        masked(instance) + overrides.filterKeys { it in deliveries.overridableKeys(instance.plugin) }

    /** Problems with [overrides] on [instance], by field key, checked as the plugin will receive them. */
    fun checkOverrides(instance: PluginInstance, overrides: Map<String, String>): List<FieldProblem> {
        val target = runCatching { target(instance, overrides) }.getOrElse {
            return listOf(FieldProblem("instance", "has a secret that cannot be decrypted; an admin must re-enter it"))
        }
        return deliveries.validate(instance.plugin, target)
    }

    /**
     * What the plugin is given to send: the instance's values, secrets opened, with
     * [overrides] applied to the overridable fields only. Throws
     * [SecretUnreadableException] if a secret cannot be opened.
     */
    fun target(instance: PluginInstance, overrides: Map<String, String>): Map<String, String> {
        val overridable = deliveries.overridableKeys(instance.plugin)
        return open(instance) + overrides.filterKeys { it in overridable }
    }

    // ---- checking a draft ----

    private fun build(draft: InstanceDraft, existing: PluginInstance?, user: NotifyUser): InstanceResult {
        val problems = mutableListOf<InstanceProblem>()
        fun problem(field: String, reason: String) = problems.add(InstanceProblem(field, reason))

        val group = draft.group.trim()
        val plugin = draft.plugin.trim()
        if (existing != null) {
            if (group != existing.group) problem("group", "cannot change; create an instance in the other group instead")
            if (plugin != existing.plugin) problem("plugin", "cannot change")
        } else {
            if (!groups.isNotificationGroup(group)) problem("group", if (group.isEmpty()) "must be chosen" else "is not a notification group")
            if (deliveries.plugin(plugin) == null) problem("plugin", "is not an installed plugin")
        }
        val name = draft.name.trim()
        when {
            name.isEmpty() -> problem("name", "is required")
            name.length > datafill.maxNameLength -> problem("name", "must be at most ${datafill.maxNameLength} characters")
            store.list(setOf(group)).any { it.id != existing?.id && it.name.equals(name, ignoreCase = true) } ->
                problem("name", "is already used by another instance in this group")
        }
        if (problems.isNotEmpty()) return InstanceResult.Invalid(problems.toList())

        // Secrets left blank or masked keep their stored values, opened so the plugin can
        // check the whole configuration as it will be used.
        val secrets = deliveries.secretKeys(plugin)
        val previous = existing?.let { runCatching { open(it) }.getOrNull() }
        val given = deliveries.normalize(plugin, draft.config).filterNot { (key, value) -> key in secrets && value == DeliveryService.MASK }
        val plain = given + secrets.mapNotNull { key ->
            val value = draft.config[key]?.trim()
            if (value.isNullOrEmpty() || value == DeliveryService.MASK) previous?.get(key)?.let { key to it } else null
        }
        deliveries.validate(plugin, plain).forEach { problem(if (it.field == "plugin") "plugin" else "config.${it.field}", it.reason) }
        if (problems.isNotEmpty()) return InstanceResult.Invalid(problems.toList())

        val now = clock.instant()
        return InstanceResult.Saved(
            PluginInstance(
                id = existing?.id ?: UUID.randomUUID(),
                group = group,
                plugin = plugin,
                name = name,
                config = plain.mapValues { (key, value) -> if (key in secrets) cipher.seal(value) else value },
                createdBy = existing?.createdBy ?: user.actor,
                createdAt = existing?.createdAt ?: now,
                updatedBy = user.actor,
                updatedAt = now,
            ),
        )
    }

    private fun open(instance: PluginInstance): Map<String, String> {
        val secrets = deliveries.secretKeys(instance.plugin)
        return instance.config.mapValues { (key, value) -> if (key in secrets) cipher.open(value) else value }
    }

    /** What changed, by field label; a secret is named, never shown. */
    private fun changes(before: PluginInstance, after: PluginInstance): String? {
        val fields = deliveries.plugin(after.plugin)?.fields.orEmpty()
        val secrets = deliveries.secretKeys(after.plugin)
        val beforeOpen = runCatching { open(before) }.getOrNull()
        val afterOpen = open(after)
        return buildList {
            if (before.name != after.name) add("name ${before.name} → ${after.name}")
            fields.forEach { field ->
                val was = if (field.key in secrets) beforeOpen?.get(field.key) else before.config[field.key]
                val now = if (field.key in secrets) afterOpen[field.key] else after.config[field.key]
                if (was != now) add(field.label + if (field.key in secrets) " (secret)" else "")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "changed ")
    }

    private fun record(instance: PluginInstance, type: AuditEventType, user: NotifyUser, detail: String) {
        audit.record(
            AuditEvent(
                occurredAt = clock.instant(),
                type = type,
                actor = user.actor,
                notificationId = null,
                notificationTitle = "Delivery instance: ${instance.name}",
                group = instance.group,
                plugin = instance.plugin,
                target = instance.name,
                detail = detail,
            ),
        )
    }
}
