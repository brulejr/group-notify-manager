/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import io.brulejr.groupnotify.audit.ActorActivity
import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.security.NotifyUser
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Who uses the system: a record per person, kept as they make requests, and what the
 * audit trail says they did. For admins only: anyone else is given nothing.
 *
 * Writing on every request would be wasteful, so a person's record is written at most
 * every `touch-interval`, sooner when what Authelia says about them changes or a new
 * visit begins. This does not replace LLDAP: accounts and memberships are managed there.
 */
class UserService(
    private val store: UserPort,
    private val audit: AuditService,
    private val datafill: UserDatafill,
    private val clock: Clock,
) {

    /** The latest record of each person seen since startup, so most requests write nothing. */
    private val recent = ConcurrentHashMap<String, UserRecord>()

    /** Whether [viewer] may see who uses the system. */
    fun canSee(viewer: NotifyUser) = viewer.admin

    /**
     * Notes that [user] made a request now, from [address] with [agent]. Called for every
     * signed-in request to the console and the API.
     */
    fun seen(user: NotifyUser, address: String?, agent: String?) {
        val now = clock.instant()
        recent.compute(user.username) { _, cached ->
            val before = cached ?: store.find(user.username)
            val newVisit = before == null || Duration.between(before.lastSeen, now) > datafill.visitGap
            val record = UserRecord(
                username = user.username,
                displayName = user.displayName,
                email = user.email,
                groups = user.groups,
                admin = user.admin,
                firstSeen = before?.firstSeen ?: now,
                lastSeen = now,
                visits = (before?.visits ?: 0) + if (newVisit) 1 else 0,
                visitStartedAt = if (newVisit || before == null) now else before.visitStartedAt,
                lastAddress = address?.take(100) ?: before?.lastAddress,
                lastAgent = agent?.take(300) ?: before?.lastAgent,
            )
            val changed = before == null || newVisit ||
                before.displayName != record.displayName || before.email != record.email ||
                before.groups != record.groups || before.admin != record.admin || before.lastAddress != record.lastAddress
            if (changed || Duration.between(before!!.lastSeen, now) >= datafill.touchInterval) {
                store.save(record)
                record
            } else {
                // Not written: keep the stored last-seen, so the next write is not put off for ever.
                before
            }
        }
    }

    /** Everyone seen, most recently first; nothing for anyone but an admin. */
    fun list(viewer: NotifyUser): List<UserRecord> = if (canSee(viewer)) store.list() else emptyList()

    /** One person's record, for an admin. */
    fun find(viewer: NotifyUser, username: String): UserRecord? = if (canSee(viewer)) store.find(username) else null

    /** Per person, what they did in the last [days] days, from the audit trail; for an admin. */
    fun activity(viewer: NotifyUser, days: Long = ACTIVITY_DAYS): Map<String, ActorActivity> =
        if (canSee(viewer)) audit.activity(clock.instant().minus(Duration.ofDays(days))) else emptyMap()

    /** What [username] did lately, newest first; for an admin. */
    fun recentActions(viewer: NotifyUser, username: String, limit: Int = 50): List<AuditEvent> =
        if (canSee(viewer)) audit.byActor(username, limit) else emptyList()

    /**
     * Deletes [username]'s record, for an admin; they reappear if they come back. Their
     * audit trail is kept, as it is for every change.
     */
    fun forget(viewer: NotifyUser, username: String): Boolean {
        if (!canSee(viewer)) return false
        recent.remove(username)
        return store.delete(username)
    }

    /** Drops what is remembered between requests; for tests, whose stores are emptied between them. */
    internal fun clearCache() = recent.clear()

    /** Their page in LLDAP, when its address is configured. */
    fun lldapLink(username: String): String? =
        datafill.lldapUrl.trimEnd('/').takeIf { it.isNotBlank() }?.let { "$it/user/$username" }

    /** How many were seen in the last day, and in all; for health. */
    fun counts(): Pair<Int, Int> {
        val all = store.list()
        val since = clock.instant().minus(Duration.ofDays(1))
        return all.count { it.lastSeen >= since } to all.size
    }

    companion object {
        const val ACTIVITY_DAYS = 30L
    }
}
