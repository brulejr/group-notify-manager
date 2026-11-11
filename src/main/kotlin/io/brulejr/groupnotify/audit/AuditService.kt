/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.security.NotifyUser
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * The history of every notification: who changed it, and every delivery made of it.
 *
 * Readers see only their own groups' history, as with the notifications themselves;
 * admins see all of it.
 */
class AuditService(
    private val store: AuditPort,
    private val groups: GroupService,
    private val datafill: AuditDatafill,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(AuditService::class.java)
    private val lastPurge = AtomicReference(Instant.EPOCH)

    fun record(event: AuditEvent): AuditEvent {
        val stored = store.record(event)
        purgeIfDue()
        return stored
    }

    /** What [viewer] may see of [query]: it is narrowed to their groups. */
    fun history(viewer: NotifyUser, query: AuditQuery): List<AuditEvent> = store.find(scoped(viewer, query))

    /** How many of each kind of event [viewer] may see since [since]. */
    fun counts(viewer: NotifyUser, since: Instant): Map<AuditEventType, Long> =
        store.count(scoped(viewer, AuditQuery(since = since)))

    /** Every group's counts since [since], for health. */
    fun counts(since: Instant): Map<AuditEventType, Long> = store.count(AuditQuery(since = since))

    private fun scoped(viewer: NotifyUser, query: AuditQuery): AuditQuery {
        val allowed = groups.visibilityFilter(viewer) ?: return query
        return query.copy(groups = query.groups?.intersect(allowed) ?: allowed)
    }

    private fun purgeIfDue() {
        val now = clock.instant()
        val last = lastPurge.get()
        if (Duration.between(last, now) < PURGE_EVERY || !lastPurge.compareAndSet(last, now)) return
        runCatching { store.purge(now.minus(datafill.retention)) }
            .onSuccess { if (it > 0) log.info("deleted {} audit event(s) older than {}", it, datafill.retention) }
            .onFailure { log.warn("could not delete old audit events", it) }
    }

    private companion object {
        val PURGE_EVERY: Duration = Duration.ofHours(1)
    }
}
