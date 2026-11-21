/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user.web

import io.brulejr.groupnotify.audit.ActorActivity
import io.brulejr.groupnotify.user.UserRecord

/**
 * A person as the Users page lists them.
 *
 * @property messagingGroups their groups that hold messages and events.
 * @property otherGroups the rest, such as `homelab_users`.
 * @property activity what they did in the last 30 days; null when nothing.
 * @property lldap their LLDAP page, when its address is configured.
 * @property browser their client in a few words, e.g. `Firefox on Linux`.
 */
data class UserRow(
    val record: UserRecord,
    val messagingGroups: MutableList<String>,
    val otherGroups: MutableList<String>,
    val activity: ActorActivity?,
    val lldap: String?,
    val browser: String?,
)
