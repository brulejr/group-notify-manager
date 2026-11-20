/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import io.brulejr.groupnotify.instance.InstanceService
import java.util.UUID

/** Checks the deliveries of a notification or an event against its group's instances, and builds them. */
class DeliveryRules(private val instances: InstanceService) {

    /**
     * Each delivery must name one of [group]'s instances, once; its overrides are kept
     * only for the plugin's overridable fields and where they differ from the instance.
     */
    fun build(
        drafts: List<NotificationDraft.Delivery>,
        group: String,
        existing: List<NotificationDelivery>,
        maxDeliveries: Int,
        problem: (String, String) -> Boolean,
        prefix: String = "deliveries",
    ): List<NotificationDelivery> {
        if (drafts.isEmpty()) problem(prefix, "add at least one delivery")
        if (drafts.size > maxDeliveries) problem(prefix, "at most $maxDeliveries deliveries")
        val previous = existing.associateBy { it.id.toString() }
        val chosen = mutableSetOf<UUID>()
        return drafts.mapIndexedNotNull { index, draft ->
            val at = "$prefix[$index]"
            val instance = runCatching { UUID.fromString(draft.instance.trim()) }.getOrNull()?.let(instances::get)
            when {
                instance == null -> {
                    problem("$at.instance", "is not a delivery instance")
                    return@mapIndexedNotNull null
                }
                // An instance of another group would send where this group may not.
                instance.group != group -> {
                    problem("$at.instance", "${instance.name} belongs to another group")
                    return@mapIndexedNotNull null
                }
                !chosen.add(instance.id) -> problem("$at.instance", "${instance.name} is chosen more than once")
            }
            val overrides = instances.overrides(checkNotNull(instance), draft.overrides)
            instances.checkOverrides(instance, overrides).forEach {
                problem(if (it.field in setOf("plugin", "instance")) "$at.instance" else "$at.overrides.${it.field}", it.reason)
            }
            val id = draft.id?.let { previous[it] }?.id ?: UUID.randomUUID()
            NotificationDelivery(id, instance.id, overrides, draft.enabled)
        }
    }
}
