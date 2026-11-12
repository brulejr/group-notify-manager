/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.security.NotifyUser
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Notifications: creating, changing, pausing and deleting them, and who may do so.
 *
 * Every method that takes a user answers only for notifications that user may see,
 * which are their own groups' unless they are an admin. One they may not see is
 * reported as not found, so its existence is not given away. Every change is recorded
 * in the audit trail.
 *
 * The scheduler's own methods, [due] and [advance], take no user.
 */
class NotificationService(
    private val store: NotificationPort,
    private val groups: GroupService,
    private val instances: InstanceService,
    private val audit: AuditService,
    private val datafill: NotificationDatafill,
    private val clock: Clock,
) {

    val defaultZone: ZoneId get() = datafill.zone

    /** The notification, if [viewer] may see it. */
    fun find(id: UUID, viewer: NotifyUser): Notification? = store.find(id)?.takeIf { groups.canView(viewer, it.group) }

    fun list(viewer: NotifyUser, filter: NotificationFilter = NotificationFilter()): List<Notification> {
        val needle = filter.q?.trim().orEmpty()
        return store.list(groups.visibilityFilter(viewer))
            .filter { filter.group.isNullOrBlank() || it.group == filter.group }
            .filter { filter.status == null || it.status == filter.status }
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) || it.subject.contains(needle, ignoreCase = true) }
            .sortedWith(compareBy<Notification>({ it.nextRunAt == null }, { it.nextRunAt }, { it.title.lowercase() }))
    }

    /** The next sends [viewer] may see, soonest first. */
    fun upcoming(viewer: NotifyUser, limit: Int): List<Notification> =
        store.list(groups.visibilityFilter(viewer)).filter { it.nextRunAt != null }.sortedBy { it.nextRunAt }.take(limit)

    fun canEdit(viewer: NotifyUser, notification: Notification) = groups.canEdit(viewer, notification.group)

    /** Every group that has a notification, whoever may see it. */
    fun groupsInUse(): Set<String> = store.groups()

    fun create(draft: NotificationDraft, user: NotifyUser): NotificationResult {
        val built = build(draft, null, user)
        if (built is NotificationResult.Saved) {
            store.save(built.notification)
            record(built.notification, AuditEventType.CREATED, user, describeNext(built.notification))
        }
        return built
    }

    fun update(id: UUID, draft: NotificationDraft, user: NotifyUser): NotificationResult {
        val existing = find(id, user) ?: return NotificationResult.NotFound
        if (!canEdit(user, existing)) return NotificationResult.Forbidden
        val built = build(draft, existing, user)
        if (built is NotificationResult.Saved) {
            store.save(built.notification)
            val changed = changes(existing, built.notification)
            record(built.notification, AuditEventType.UPDATED, user, listOfNotNull(changed, describeNext(built.notification)).joinToString("; "))
        }
        return built
    }

    /** Holds a notification: nothing is sent until it is resumed. */
    fun pause(id: UUID, user: NotifyUser): NotificationResult = change(id, user) { existing ->
        if (existing.status != NotificationStatus.ACTIVE) return@change invalid("status", "only an active notification can be paused")
        val paused = existing.copy(status = NotificationStatus.PAUSED, nextRunAt = null, updatedBy = user.actor, updatedAt = clock.instant())
        store.save(paused)
        record(paused, AuditEventType.PAUSED, user)
        NotificationResult.Saved(paused)
    }

    /**
     * Picks a paused notification up again from now: a recurring one at its next run,
     * not the runs it missed; a one-off at its time, which must not have passed.
     */
    fun resume(id: UUID, user: NotifyUser): NotificationResult = change(id, user) { existing ->
        if (existing.status != NotificationStatus.PAUSED) return@change invalid("status", "only a paused notification can be resumed")
        val next = existing.schedule.nextAfter(clock.instant())
            ?: return@change invalid("schedule", if (existing.schedule.kind == ScheduleKind.ONCE) "its time has passed; edit it to choose a new one" else "it has no runs left before its end date")
        val resumed = existing.copy(status = NotificationStatus.ACTIVE, nextRunAt = next, updatedBy = user.actor, updatedAt = clock.instant())
        store.save(resumed)
        record(resumed, AuditEventType.RESUMED, user, describeNext(resumed))
        NotificationResult.Saved(resumed)
    }

    fun delete(id: UUID, user: NotifyUser): NotificationResult = change(id, user) { existing ->
        store.delete(id)
        record(existing, AuditEventType.DELETED, user)
        NotificationResult.Saved(existing)
    }

    // ---- the scheduler's ----

    /** Active notifications due at [now], soonest first, whoever's they are. */
    fun due(now: Instant, limit: Int): List<Notification> = store.due(now, limit)

    /** See [NotificationPort.advance]. */
    fun advance(notification: Notification, nextRunAt: Instant?, ranAt: Instant): Boolean = store.advance(
        notification.id,
        checkNotNull(notification.nextRunAt) { "only a due notification can be advanced" },
        nextRunAt,
        if (nextRunAt == null) NotificationStatus.COMPLETED else NotificationStatus.ACTIVE,
        ranAt,
    )

    /** A notification by id, whoever's it is: for the scheduler, never for a request. */
    fun get(id: UUID): Notification? = store.find(id)

    // ---- checking a draft ----

    private fun build(draft: NotificationDraft, existing: Notification?, user: NotifyUser): NotificationResult {
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

        val zone = draft.zone.trim().ifEmpty { datafill.defaultZone }.let { runCatching { ZoneId.of(it) }.getOrNull() }
        if (zone == null) problem("zone", "is not a time zone")
        val schedule = zone?.let { schedule(draft, it, existing, now, ::problem) }
        val built = deliveries(draft.deliveries, group, existing, ::problem)

        if (problems.isNotEmpty() || schedule == null) return NotificationResult.Invalid(problems.toList())

        val next = schedule.nextAfter(now)
        val status = when {
            existing == null -> if (draft.paused) NotificationStatus.PAUSED else NotificationStatus.ACTIVE
            existing.status == NotificationStatus.PAUSED -> NotificationStatus.PAUSED
            next == null -> NotificationStatus.COMPLETED
            else -> NotificationStatus.ACTIVE
        }
        return NotificationResult.Saved(
            Notification(
                id = existing?.id ?: UUID.randomUUID(),
                group = group,
                title = title,
                subject = subject,
                body = draft.body,
                format = draft.format,
                schedule = schedule,
                status = status,
                deliveries = built,
                nextRunAt = next.takeIf { status == NotificationStatus.ACTIVE },
                lastRunAt = existing?.lastRunAt,
                createdBy = existing?.createdBy ?: user.actor,
                createdAt = existing?.createdAt ?: now,
                updatedBy = user.actor,
                updatedAt = now,
            ),
        )
    }

    private fun schedule(
        draft: NotificationDraft,
        zone: ZoneId,
        existing: Notification?,
        now: Instant,
        problem: (String, String) -> Boolean,
    ): Schedule? = when (draft.kind) {
        ScheduleKind.ONCE -> {
            val runAt = draft.runAt?.atZone(zone)?.toInstant()
            when {
                runAt == null -> null.also { problem("runAt", "is required") }
                // A one-off already sent can still be edited, its wording say, without
                // choosing a new time; choosing one sends it again.
                runAt <= now && !(existing?.status == NotificationStatus.COMPLETED && existing.schedule.runAt == runAt) ->
                    null.also { problem("runAt", "must be in the future") }
                else -> Schedule(ScheduleKind.ONCE, zone, runAt = runAt)
            }
        }
        ScheduleKind.RECURRING -> {
            val cron = draft.cron.trim()
            val cronProblem = if (cron.isEmpty()) "is required" else CronSpec.problem(cron)
            if (cronProblem != null) {
                null.also { problem("cron", cronProblem) }
            } else {
                val until = draft.until?.atTime(LocalTime.MAX)?.atZone(zone)?.toInstant()
                val schedule = Schedule(ScheduleKind.RECURRING, zone, cron = cron, until = until)
                if (until != null && until <= now) {
                    null.also { problem("until", "must not have passed") }
                } else if (schedule.nextAfter(now) == null) {
                    null.also { problem("cron", "never runs before the end date") }
                } else {
                    schedule
                }
            }
        }
    }

    /**
     * Each delivery must name one of [group]'s instances, once; its overrides are kept
     * only for the plugin's overridable fields and where they differ from the instance.
     */
    private fun deliveries(
        drafts: List<NotificationDraft.Delivery>,
        group: String,
        existing: Notification?,
        problem: (String, String) -> Boolean,
    ): List<NotificationDelivery> {
        if (drafts.isEmpty()) problem("deliveries", "add at least one delivery")
        if (drafts.size > datafill.maxDeliveries) problem("deliveries", "at most ${datafill.maxDeliveries} deliveries")
        val previous = existing?.deliveries.orEmpty().associateBy { it.id.toString() }
        val chosen = mutableSetOf<UUID>()
        return drafts.mapIndexedNotNull { index, draft ->
            val prefix = "deliveries[$index]"
            val instance = runCatching { UUID.fromString(draft.instance.trim()) }.getOrNull()?.let(instances::get)
            when {
                instance == null -> {
                    problem("$prefix.instance", "is not a delivery instance")
                    return@mapIndexedNotNull null
                }
                // An instance of another group would send where this group may not.
                instance.group != group -> {
                    problem("$prefix.instance", "${instance.name} belongs to another group")
                    return@mapIndexedNotNull null
                }
                !chosen.add(instance.id) -> problem("$prefix.instance", "${instance.name} is chosen more than once")
            }
            val overrides = instances.overrides(checkNotNull(instance), draft.overrides)
            instances.checkOverrides(instance, overrides).forEach {
                problem(if (it.field in setOf("plugin", "instance")) "$prefix.instance" else "$prefix.overrides.${it.field}", it.reason)
            }
            val id = draft.id?.let { previous[it] }?.id ?: UUID.randomUUID()
            NotificationDelivery(id, instance.id, overrides, draft.enabled)
        }
    }

    // ---- shared ----

    private fun change(id: UUID, user: NotifyUser, block: (Notification) -> NotificationResult): NotificationResult {
        val existing = find(id, user) ?: return NotificationResult.NotFound
        if (!canEdit(user, existing)) return NotificationResult.Forbidden
        return block(existing)
    }

    private fun invalid(field: String, reason: String) = NotificationResult.Invalid(listOf(NotificationProblem(field, reason)))

    private fun record(notification: Notification, type: AuditEventType, user: NotifyUser, detail: String = "") {
        audit.record(
            AuditEvent(
                occurredAt = clock.instant(),
                type = type,
                actor = user.actor,
                notificationId = notification.id,
                notificationTitle = notification.title,
                group = notification.group,
                detail = detail,
            ),
        )
    }

    private fun describeNext(notification: Notification): String =
        notification.nextRunAt?.let { "next run $it" } ?: if (notification.status == NotificationStatus.PAUSED) "paused" else ""

    private fun changes(before: Notification, after: Notification): String? = buildList {
        if (before.group != after.group) add("group ${before.group} → ${after.group}")
        if (before.title != after.title) add("title")
        if (before.subject != after.subject) add("subject")
        if (before.body != after.body || before.format != after.format) add("body")
        if (before.schedule != after.schedule) add("schedule")
        // By content: a delivery re-entered as it was is no change, whatever its id.
        if (before.deliveries.map { it.copy(id = NO_ID) } != after.deliveries.map { it.copy(id = NO_ID) }) add("deliveries")
    }.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "changed ")

    private companion object {
        val NO_ID: UUID = UUID(0, 0)
    }
}
