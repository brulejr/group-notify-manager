/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user.postgres

import io.brulejr.groupnotify.platform.PostgresTestDatabase
import io.brulejr.groupnotify.user.UserRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresUserStoreTest {

    private val store = PostgresUserStore(PostgresTestDatabase.jdbc, JsonMapper.builder().build())
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun record(username: String, lastSeen: Instant = now) = UserRecord(
        username, "Name of $username", "$username@example.org", setOf("notify_club", "homelab_users"), admin = false,
        firstSeen = now.minusSeconds(3600), lastSeen = lastSeen, visits = 2, visitStartedAt = lastSeen.minusSeconds(60),
        lastAddress = "203.0.113.7", lastAgent = "Firefox",
    )

    @BeforeEach
    fun reset() = PostgresTestDatabase.reset()

    @Test
    fun `a record comes back as saved, updated in place, most recently seen first`() {
        store.save(record("alice"))
        store.save(record("bob", lastSeen = now.plusSeconds(60)))
        assertEquals(record("alice"), store.find("alice"))

        store.save(record("alice", lastSeen = now.plusSeconds(120)).copy(visits = 3))
        assertEquals(listOf("alice", "bob"), store.list().map { it.username })
        assertEquals(3, store.find("alice")!!.visits)
        assertEquals(now.minusSeconds(3600), store.find("alice")!!.firstSeen, "first seen is kept")
    }

    @Test
    fun `a record can be deleted`() {
        store.save(record("alice"))

        assertTrue(store.delete("alice"))
        assertNull(store.find("alice"))
        assertFalse(store.delete("alice"))
    }
}
