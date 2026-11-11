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
import io.brulejr.groupnotify.console.ConsoleService
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/** The console's front page: what is due next, what was sent lately, and whether every plugin is ready. */
@Controller
class DashboardController(
    private val console: ConsoleService,
    private val datafill: ConsoleDatafill,
) {

    @GetMapping("/")
    fun root() = "redirect:/ui"

    @GetMapping("/ui")
    fun dashboard(user: NotifyUser, model: Model): String {
        overview(user, model)
        return "ui/dashboard"
    }

    /** The part of the dashboard that reloads itself. */
    @GetMapping("/ui/overview")
    fun overview(user: NotifyUser, model: Model): String {
        model.addAttribute("overview", console.overview(user))
        model.addAttribute("refreshSeconds", datafill.refreshInterval.toSeconds())
        return "ui/dashboard :: overview"
    }
}
