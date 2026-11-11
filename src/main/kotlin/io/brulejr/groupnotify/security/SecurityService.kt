/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

/**
 * Turns what Authelia says about someone, through headers or an ID token, into a
 * [NotifyUser] with the roles this application gives them.
 */
class SecurityService(private val datafill: SecurityDatafill) {

    fun user(username: String, displayName: String?, email: String?, groups: Collection<String>): NotifyUser {
        val groupSet = groups.map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()
        val admin = datafill.adminGroup in groupSet
        return NotifyUser(
            username = username,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: username,
            email = email?.takeIf { it.isNotBlank() },
            groups = groupSet,
            admin = admin,
            editor = admin || datafill.editorGroup == null || datafill.editorGroup in groupSet,
        )
    }

    /** `Remote-Groups` is a comma-separated list. */
    fun groupsHeader(value: String?): List<String> = value?.split(',').orEmpty()

    val mode get() = datafill.mode

    val logoutUrl: String? get() = when (datafill.mode) {
        AuthMode.FORWARD_AUTH -> datafill.logoutUrl
        AuthMode.OIDC -> "/logout"
    }

    val devUserActive get() = datafill.mode == AuthMode.FORWARD_AUTH && datafill.devUser != null
}
