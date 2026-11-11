/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

/** How a request's user is known. Either way Authelia does the signing in. */
enum class AuthMode {
    /**
     * Traefik asks Authelia about every request and passes the user on in `Remote-*`
     * headers, as iot-pipeline is published. The application has no login of its own,
     * so it must only be reachable through Traefik: bind it to loopback or the Docker
     * network, or list the proxy in `notify.security.forward-auth.trusted-proxies`.
     */
    FORWARD_AUTH,

    /**
     * The application signs users in itself, as an OpenID Connect client of Authelia,
     * and reads their groups from the ID token. For when it is published without
     * forward-auth in front of it.
     */
    OIDC,
}
