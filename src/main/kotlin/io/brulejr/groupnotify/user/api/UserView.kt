/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user.api

import io.brulejr.groupnotify.audit.ActorActivity
import io.brulejr.groupnotify.user.UserRecord
import java.time.Instant

/**
 * A person as the REST API shows an admin: their record, and what they did in the last
 * 30 days.
 */
data class UserView(
    val username: String,
    val displayName: String,
    val email: String?,
    val groups: List<String>,
    val admin: Boolean,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val visits: Int,
    val visitStartedAt: Instant,
    val lastAddress: String?,
    val lastAgent: String?,
    val recentActions: Long,
    val lastActionAt: Instant?,
    val lldap: String?,
) {
    companion object {
        fun of(r: UserRecord, activity: ActorActivity?, lldap: String?) = UserView(
            r.username, r.displayName, r.email, r.groups.sorted(), r.admin, r.firstSeen, r.lastSeen, r.visits, r.visitStartedAt,
            r.lastAddress, r.lastAgent, activity?.events ?: 0, activity?.lastAt, lldap,
        )
    }
}
