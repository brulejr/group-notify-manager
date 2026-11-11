/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Signs a request in from the `Remote-*` headers Traefik copies from Authelia's
 * forward-auth answer. A request without them stays anonymous and is refused by the
 * rules in [SecurityConfig], unless a development user is configured.
 *
 * Headers from an address outside `trusted-proxies` are ignored, so a client that can
 * reach the port directly cannot claim to be someone.
 */
class ForwardAuthFilter(
    private val datafill: SecurityDatafill,
    private val security: SecurityService,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ForwardAuthFilter::class.java)
    private val headers = datafill.forwardAuth
    private val trusted = headers.trustedProxies.map(::IpAddressMatcher)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        userFor(request)?.let { user ->
            val authorities = buildList {
                add(SimpleGrantedAuthority("ROLE_USER"))
                if (user.admin) add(SimpleGrantedAuthority("ROLE_ADMIN"))
            }
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = PreAuthenticatedAuthenticationToken(user, "N/A", authorities)
            SecurityContextHolder.setContext(context)
        }
        try {
            chain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun userFor(request: HttpServletRequest): NotifyUser? {
        val username = request.getHeader(headers.userHeader)?.trim()?.takeIf { it.isNotEmpty() }
        if (username != null) {
            if (trusted.isNotEmpty() && trusted.none { it.matches(request.remoteAddr) }) {
                log.warn("ignoring {} from {}, which is not a trusted proxy", headers.userHeader, request.remoteAddr)
                return null
            }
            return security.user(
                username,
                request.getHeader(headers.nameHeader),
                request.getHeader(headers.emailHeader),
                security.groupsHeader(request.getHeader(headers.groupsHeader)),
            )
        }
        return datafill.devUser?.let { security.user(it.username, it.name, it.email, it.groups) }
    }
}
