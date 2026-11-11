/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

/** The signed-in user, however they signed in, or null for an anonymous request. */
fun Authentication?.notifyUser(): NotifyUser? = when (val principal = this?.principal) {
    is NotifyUser -> principal
    is NotifyOidcUser -> principal.user
    else -> null
}

/** The user of the request being handled. */
fun currentUser(): NotifyUser? = SecurityContextHolder.getContext().authentication.notifyUser()
