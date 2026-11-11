/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import java.io.Serializable

/**
 * The person making a request, as Authelia describes them.
 *
 * @property groups every group Authelia lists for them, notification groups or not.
 * @property admin a member of `notify.security.admin-group`: sees every group.
 * @property editor may create, change and send notifications in their groups.
 */
data class NotifyUser(
    val username: String,
    val displayName: String,
    val email: String?,
    val groups: Set<String>,
    val admin: Boolean,
    val editor: Boolean,
) : Serializable {

    fun memberOf(group: String) = group in groups

    /** The name to record in the audit trail. */
    val actor: String get() = username
}
