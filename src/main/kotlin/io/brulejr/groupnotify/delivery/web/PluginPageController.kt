/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery.web

import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/** The installed delivery plugins, whether each is ready, and what a delivery through it asks for. */
@Controller
class PluginPageController(private val deliveries: DeliveryService) {

    @GetMapping("/ui/plugins")
    fun plugins(user: NotifyUser, model: Model): String {
        model.addAttribute("plugins", deliveries.plugins().toMutableList())
        model.addAttribute("isAdmin", user.admin)
        return "ui/plugins/list"
    }
}
