/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.postgres

import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.platform.PostgresTestDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresInstanceStoreTest {

    private val store = PostgresInstanceStore(PostgresTestDatabase.jdbc, JsonMapper.builder().build())
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun instance(group: String = "notify_club", name: String = "Club list") = PluginInstance(
        UUID.randomUUID(), group, "groupsio", name, mapOf("group" to "brulenet", "apiKey" to "enc:v1:abc"), "root", now, "root", now,
    )

    @BeforeEach
    fun reset() = PostgresTestDatabase.reset()

    @Test
    fun `an instance comes back as saved, and only its name and config change on update`() {
        val i = instance()
        store.save(i)
        assertEquals(i, store.find(i.id))

        store.save(i.copy(name = "Renamed", group = "notify_family", config = mapOf("group" to "other")))
        val updated = store.find(i.id)!!
        assertEquals("Renamed", updated.name)
        assertEquals("notify_club", updated.group, "the group is fixed once created")
        assertEquals(mapOf("group" to "other"), updated.config)
    }

    @Test
    fun `lists are limited to the groups asked for, and names are unique in a group whatever their case`() {
        store.save(instance("notify_club"))
        store.save(instance("notify_family"))

        assertEquals(listOf("notify_family"), store.list(setOf("notify_family")).map { it.group })
        assertTrue(store.list(emptySet()).isEmpty())
        assertFailsWith<DataIntegrityViolationException> { store.save(instance("notify_club", "CLUB LIST")) }
    }

    @Test
    fun `an instance a delivery uses cannot be deleted`() {
        val i = instance().also(store::save)
        val notification = UUID.randomUUID()
        PostgresTestDatabase.jdbc.sql(
            """
            INSERT INTO notifications (id, group_name, title, subject, body, body_format, schedule_kind, zone, status, created_by, created_at, updated_by, updated_at)
            VALUES (:n, 'notify_club', 't', 's', 'b', 'MARKDOWN', 'ONCE', 'UTC', 'PAUSED', 'a', now(), 'a', now())
            """,
        ).param("n", notification).update()
        PostgresTestDatabase.jdbc.sql("INSERT INTO deliveries (id, notification_id, position, instance_id) VALUES (:d, :n, 0, :i)")
            .param("d", UUID.randomUUID()).param("n", notification).param("i", i.id).update()

        assertFailsWith<DataIntegrityViolationException> { store.delete(i.id) }
        PostgresTestDatabase.jdbc.sql("DELETE FROM notifications").update()
        assertTrue(store.delete(i.id))
        assertNull(store.find(i.id))
    }
}
