/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Sends the old `/ui/notifications…` and `/api/notifications…` paths to their
 * `/messages` names, so bookmarks and scripts keep working. A 308 keeps the method and
 * body, so an old `POST` or `PUT` still lands; it runs after security, as any request does.
 */
class LegacyPathRedirect : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest) = renamed(path(request)) == null

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val target = request.contextPath + checkNotNull(renamed(path(request))) + (request.queryString?.let { "?$it" } ?: "")
        response.status = HttpStatus.PERMANENT_REDIRECT.value()
        response.setHeader(HttpHeaders.LOCATION, target)
    }

    private fun path(request: HttpServletRequest) = request.requestURI.removePrefix(request.contextPath)

    companion object {
        private val OLD = Regex("^/(ui|api)/notifications(?=/|$)")

        /** The new path for an old one, or null when [path] is not old. */
        fun renamed(path: String): String? = OLD.find(path)?.let { path.replaceRange(it.range, "/${it.groupValues[1]}/messages") }
    }
}
