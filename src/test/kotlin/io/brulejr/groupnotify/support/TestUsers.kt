/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.support

import io.brulejr.groupnotify.security.NotifyUser

/** The people the tests act as. */
object TestUsers {
    /** In the club only. */
    val alice = NotifyUser("alice", "Alice", "alice@example.org", setOf("notify_club", "homelab_users"), admin = false)

    /** In the family group only. */
    val bob = NotifyUser("bob", "Bob", null, setOf("notify_family"), admin = false)

    /** An admin in no notification group. */
    val admin = NotifyUser("root", "Root", null, setOf("homelab_admins"), admin = true)
}
