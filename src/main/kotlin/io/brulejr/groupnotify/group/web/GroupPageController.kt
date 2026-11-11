/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.group.web

import io.brulejr.groupnotify.group.GroupDirectory
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/** The groups page: each group the user may see, and how many notifications it has. */
@Controller
class GroupPageController(private val directory: GroupDirectory) {

    @GetMapping("/ui/groups")
    fun groups(user: NotifyUser, model: Model): String {
        model.addAttribute("groups", directory.summaries(user).toMutableList())
        return "ui/groups/list"
    }
}
