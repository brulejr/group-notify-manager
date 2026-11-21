/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

/** Where user records are kept. Storage only: [UserService] decides who may read them. */
interface UserPort {

    fun find(username: String): UserRecord?

    /** Every record, most recently seen first. */
    fun list(): List<UserRecord>

    /** Stores [record], replacing any earlier one for its username. */
    fun save(record: UserRecord)

    fun delete(username: String): Boolean
}
