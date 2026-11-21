/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user.api

import io.brulejr.groupnotify.security.NotifyUser
import io.brulejr.groupnotify.user.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Who uses the system, for admins only; anyone else is refused. */
@RestController
@RequestMapping("/api/users")
class UserController(private val users: UserService) {

    @GetMapping
    fun list(user: NotifyUser): ResponseEntity<List<UserView>> {
        if (!users.canSee(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val activity = users.activity(user)
        return ResponseEntity.ok(users.list(user).map { UserView.of(it, activity[it.username], users.lldapLink(it.username)) })
    }

    @GetMapping("/{username}")
    fun get(user: NotifyUser, @PathVariable username: String): ResponseEntity<UserView> {
        if (!users.canSee(user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val record = users.find(user, username) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(UserView.of(record, users.activity(user)[username], users.lldapLink(username)))
    }

    /** Forgets a person's record; their audit trail is kept, and they reappear if they come back. */
    @DeleteMapping("/{username}")
    fun forget(user: NotifyUser, @PathVariable username: String): ResponseEntity<Void> = when {
        !users.canSee(user) -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        users.forget(user, username) -> ResponseEntity.noContent().build()
        else -> ResponseEntity.notFound().build()
    }
}
