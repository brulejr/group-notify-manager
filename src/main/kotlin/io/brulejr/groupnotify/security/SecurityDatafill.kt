/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Who users are and what they may do. Groups come from Authelia, which takes them from
 * LLDAP; nothing about membership is stored here.
 *
 * @property adminGroup its members see and manage every group's notifications, and the
 *   management endpoints.
 * @property editorGroup when set, only its members (and admins) may create, change or
 *   send notifications; other members of a group can only read that group's. Unset,
 *   every member of a group may manage its notifications.
 * @property logoutUrl where the console's sign-out link goes in forward-auth mode, e.g.
 *   `https://auth.example.org/logout`. In OIDC mode sign-out is the application's own.
 * @property devUser signs in every request that carries no forward-auth headers as this
 *   user. For development only: set it in application-local.yml, never in production.
 */
@ConfigurationProperties("notify.security")
data class SecurityDatafill(
    val mode: AuthMode = AuthMode.FORWARD_AUTH,
    val adminGroup: String = "homelab_admins",
    val editorGroup: String? = null,
    val logoutUrl: String? = null,
    val forwardAuth: ForwardAuth = ForwardAuth(),
    val oidc: Oidc = Oidc(),
    val devUser: DevUser? = null,
) {
    init {
        require(adminGroup.isNotBlank()) { "notify.security.admin-group must not be blank" }
    }

    /**
     * The headers Authelia's forward-auth endpoint answers with, which Traefik copies onto
     * the request (`authResponseHeaders`).
     *
     * @property trustedProxies addresses or CIDR ranges the headers are believed from,
     *   e.g. `172.16.0.0/12`. Empty trusts any sender, which is only safe when nothing but
     *   the proxy can reach the port.
     */
    data class ForwardAuth(
        val userHeader: String = "Remote-User",
        val groupsHeader: String = "Remote-Groups",
        val nameHeader: String = "Remote-Name",
        val emailHeader: String = "Remote-Email",
        val trustedProxies: List<String> = emptyList(),
    )

    /**
     * The OpenID Connect client. The provider itself is Spring Boot's
     * `spring.security.oauth2.client.registration.<registration-id>.*`.
     *
     * @property groupsClaim Authelia puts groups in `groups` when the client asks for the
     *   `groups` scope.
     */
    data class Oidc(
        val registrationId: String = "authelia",
        val usernameClaim: String = "preferred_username",
        val groupsClaim: String = "groups",
    )

    data class DevUser(
        val username: String,
        val name: String = username,
        val email: String? = null,
        val groups: List<String> = emptyList(),
    )
}
