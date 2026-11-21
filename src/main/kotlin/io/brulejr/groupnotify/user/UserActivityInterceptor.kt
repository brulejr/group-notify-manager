/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import io.brulejr.groupnotify.security.currentUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Tells [UserService] about every signed-in request to the console and the API, with the
 * client's address and browser. A failure to record never fails the request.
 */
class UserActivityInterceptor(private val users: UserService) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(UserActivityInterceptor::class.java)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val user = currentUser() ?: return true
        try {
            users.seen(user, address(request), request.getHeader("User-Agent"))
        } catch (e: Exception) {
            log.warn("could not record a request by {}", user.username, e)
        }
        return true
    }

    /** The client as the proxy saw it: the first address in X-Forwarded-For, else the peer's. */
    private fun address(request: HttpServletRequest): String? =
        request.getHeader("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: request.remoteAddr
}
