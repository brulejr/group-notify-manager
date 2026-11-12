/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery.web

import io.brulejr.groupnotify.console.web.Toast
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * The Delivery plugins page: the installed plugins, whether each is ready and what an
 * instance of it is set up with. The instances themselves are loaded into it from
 * `/ui/instances/list`.
 */
@Controller
class PluginPageController(private val deliveries: DeliveryService) {

    @GetMapping("/ui/plugins")
    fun plugins(user: NotifyUser, @RequestParam(required = false) saved: String?, model: Model): String {
        model.addAttribute("plugins", deliveries.plugins().toMutableList())
        model.addAttribute("isAdmin", user.admin)
        if (saved != null) model.addAttribute("toast", Toast.success("Delivery instance saved"))
        return "ui/plugins/list"
    }
}
