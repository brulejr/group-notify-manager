/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser

/** Reads the username and groups from Authelia's claims when someone signs in over OIDC. */
class NotifyOidcUserService(
    private val datafill: SecurityDatafill.Oidc,
    private val security: SecurityService,
) : OAuth2UserService<OidcUserRequest, OidcUser> {

    private val delegate = OidcUserService()

    override fun loadUser(request: OidcUserRequest): OidcUser {
        val oidc = delegate.loadUser(request)
        val groups = when (val claim = oidc.claims[datafill.groupsClaim]) {
            is Collection<*> -> claim.map { it.toString() }
            is String -> claim.split(',')
            else -> emptyList()
        }
        val user = security.user(
            username = checkNotNull(oidc.getClaimAsString(datafill.usernameClaim) ?: oidc.subject) { "the ID token names no user" },
            displayName = oidc.fullName,
            email = oidc.email,
            groups = groups,
        )
        val authorities = oidc.authorities + SimpleGrantedAuthority("ROLE_USER") +
            listOfNotNull(SimpleGrantedAuthority("ROLE_ADMIN").takeIf { user.admin })
        return NotifyOidcUser(user, DefaultOidcUser(authorities, oidc.idToken, oidc.userInfo, "sub"))
    }
}
