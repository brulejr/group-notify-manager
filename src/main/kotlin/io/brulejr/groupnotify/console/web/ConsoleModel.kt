/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console.web

import io.brulejr.groupnotify.console.ConsoleDatafill
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.security.NotifyUser
import io.brulejr.groupnotify.security.SecurityService
import io.brulejr.groupnotify.security.currentUser
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * What every console page's frame needs: who is signed in, the CSRF token htmx sends
 * back, and where signing out goes. Applies to the page controllers only.
 */
@ControllerAdvice(basePackages = ["io.brulejr.groupnotify"], annotations = [org.springframework.stereotype.Controller::class])
class ConsoleModel(
    private val datafill: ConsoleDatafill,
    private val security: SecurityService,
    private val groups: GroupService,
) {

    @ModelAttribute("me")
    fun me(): NotifyUser? = currentUser()

    /**
     * Read here, before the page renders, so the CSRF cookie is written while the
     * response can still take headers.
     */
    @ModelAttribute("csrf")
    fun csrf(request: HttpServletRequest): CsrfToken? =
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.also { it.token }

    @ModelAttribute("consoleTitle")
    fun title() = datafill.title

    @ModelAttribute("logoutUrl")
    fun logoutUrl(): String? = security.logoutUrl

    @ModelAttribute("devUser")
    fun devUser() = security.devUserActive

    @ModelAttribute("groupNames")
    fun groupNames(): Map<String, String> = groups.displayNames()
}
