/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import org.springframework.security.oauth2.core.oidc.user.OidcUser

/** An OIDC sign-in, carrying the [NotifyUser] made from its claims once, at sign-in. */
class NotifyOidcUser(val user: NotifyUser, private val delegate: OidcUser) : OidcUser by delegate {
    override fun getName(): String = user.username
}
