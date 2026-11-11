/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.group.api

import io.brulejr.groupnotify.group.GroupDirectory
import io.brulejr.groupnotify.group.GroupSummary
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** The notification groups the caller may see: their own, and for an admin every group in use. */
@RestController
class GroupController(private val directory: GroupDirectory) {

    @GetMapping("/api/groups")
    fun list(user: NotifyUser): List<GroupSummary> = directory.summaries(user)
}
