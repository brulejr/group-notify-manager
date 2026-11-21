/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import java.time.Instant

/**
 * Someone who has used the console or the API, as Authelia last described them, and when.
 * Nothing here decides what they may do: that comes from their groups on each request.
 *
 * With forward-auth the application never sees a sign-in, so a **visit** stands in for
 * one: requests with no gap longer than `notify.users.visit-gap` between them.
 *
 * @property groups every group Authelia last listed, messaging groups or not.
 * @property visitStartedAt when their latest visit began: the nearest thing to a last sign-in.
 * @property lastAddress the client's address as the proxy reported it (`X-Forwarded-For`).
 * @property lastAgent their browser or client, as it names itself.
 */
data class UserRecord(
    val username: String,
    val displayName: String,
    val email: String?,
    val groups: Set<String>,
    val admin: Boolean,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val visits: Int,
    val visitStartedAt: Instant,
    val lastAddress: String?,
    val lastAgent: String?,
)
