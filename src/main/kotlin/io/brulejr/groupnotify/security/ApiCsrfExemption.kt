/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * REST calls that a browser could not have been tricked into sending from another site,
 * and so need no CSRF token: a JSON body, or an `X-Requested-With` header. Either makes
 * a cross-origin request preflighted, and this application allows no cross-origin calls.
 * Everything else, the console's forms included, carries the token.
 */
object ApiCsrfExemption : RequestMatcher {
    override fun matches(request: HttpServletRequest): Boolean {
        if (!request.requestURI.removePrefix(request.contextPath).startsWith("/api/")) return false
        val json = request.contentType?.startsWith("application/json", ignoreCase = true) == true
        return json || request.getHeader("X-Requested-With") != null
    }
}
