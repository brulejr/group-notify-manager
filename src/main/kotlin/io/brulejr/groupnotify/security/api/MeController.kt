/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security.api

import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Who the application thinks the caller is: the quickest check that Authelia's headers arrive. */
@RestController
class MeController {

    @GetMapping("/api/me")
    fun me(user: NotifyUser): NotifyUser = user
}
