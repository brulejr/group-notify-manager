/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import java.util.concurrent.ConcurrentHashMap

/** In-memory [UserPort], for running without a database. Nothing survives a restart. */
class InMemoryUserStore : UserPort {

    private val byName = ConcurrentHashMap<String, UserRecord>()

    override fun find(username: String): UserRecord? = byName[username]

    override fun list(): List<UserRecord> = byName.values.sortedByDescending { it.lastSeen }

    override fun save(record: UserRecord) {
        byName[record.username] = record
    }

    override fun delete(username: String): Boolean = byName.remove(username) != null
}
