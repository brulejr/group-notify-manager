/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user.web

import io.brulejr.groupnotify.console.web.Htmx
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.security.NotifyUser
import io.brulejr.groupnotify.user.UserRecord
import io.brulejr.groupnotify.user.UserService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.server.ResponseStatusException

/** The admins' Users page: who uses the system, when, from where, and what they did. */
@Controller
@RequestMapping("/ui/users")
class UserPageController(private val users: UserService, private val groups: GroupService) {

    @GetMapping
    fun list(user: NotifyUser, model: Model): String {
        requireAdmin(user)
        val activity = users.activity(user)
        model.addAttribute("rows", users.list(user).map { row(it, activity[it.username]) }.toMutableList())
        model.addAttribute("activityDays", UserService.ACTIVITY_DAYS)
        return "ui/users/list"
    }

    @GetMapping("/{username}")
    fun detail(user: NotifyUser, @PathVariable username: String, model: Model): String {
        requireAdmin(user)
        val record = users.find(user, username) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Nobody of that name has been seen")
        model.addAttribute("row", row(record, users.activity(user)[username]))
        model.addAttribute("actions", users.recentActions(user, username).toMutableList())
        model.addAttribute("activityDays", UserService.ACTIVITY_DAYS)
        return "ui/users/detail"
    }

    /** Forgets a person's record and goes back to the list. */
    @DeleteMapping("/{username}")
    fun forget(user: NotifyUser, @PathVariable username: String, response: HttpServletResponse): String {
        requireAdmin(user)
        if (!users.forget(user, username)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        Htmx.redirect(response, "/ui/users")
        return "ui/layout :: toastOnly"
    }

    private fun row(record: UserRecord, activity: io.brulejr.groupnotify.audit.ActorActivity?): UserRow {
        val (messaging, other) = record.groups.sorted().partition(groups::isNotificationGroup)
        return UserRow(record, messaging.toMutableList(), other.toMutableList(), activity, users.lldapLink(record.username), Browsers.describe(record.lastAgent))
    }

    private fun requireAdmin(user: NotifyUser) {
        if (!users.canSee(user)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins see who uses the system")
    }
}
