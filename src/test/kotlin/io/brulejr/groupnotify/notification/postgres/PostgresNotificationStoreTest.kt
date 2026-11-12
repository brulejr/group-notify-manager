/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.postgres

import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.instance.postgres.PostgresInstanceStore
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.notification.NotificationDelivery
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.notification.Schedule
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.platform.PostgresTestDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class PostgresNotificationStoreTest {

    private val json = JsonMapper.builder().build()
    private val store = PostgresNotificationStore(PostgresTestDatabase.jdbc, PostgresTestDatabase.transactions, json)
    private val instances = PostgresInstanceStore(PostgresTestDatabase.jdbc, json)
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

    // A delivery's instance is a foreign key, so both must exist first.
    private lateinit var discord: PluginInstance
    private lateinit var groupsio: PluginInstance

    private fun instance(plugin: String, name: String) = PluginInstance(
        UUID.randomUUID(), "notify_club", plugin, name, mapOf("k" to "v"), "root", now, "root", now,
    ).also(instances::save)

    private fun notification(group: String = "notify_club", next: Instant? = now.minusSeconds(5)) = Notification(
        id = UUID.randomUUID(), group = group, title = "t", subject = "s", body = "**b**", format = BodyFormat.MARKDOWN,
        schedule = Schedule(ScheduleKind.RECURRING, ZoneId.of("America/New_York"), cron = "0 9 * * *"),
        status = NotificationStatus.ACTIVE,
        deliveries = listOf(
            NotificationDelivery(UUID.randomUUID(), discord.id, mapOf("mention" to "@here")),
            NotificationDelivery(UUID.randomUUID(), groupsio.id, enabled = false),
        ),
        nextRunAt = next, lastRunAt = null, createdBy = "alice", createdAt = now, updatedBy = "alice", updatedAt = now,
    )

    @BeforeEach
    fun reset() {
        PostgresTestDatabase.reset()
        discord = instance("discord", "Club Discord")
        groupsio = instance("groupsio", "Club list")
    }

    @Test
    fun `a notification and its deliveries come back as saved, in order`() {
        val n = notification()
        store.save(n)

        assertEquals(n, store.find(n.id))
        store.save(n.copy(title = "renamed", deliveries = n.deliveries.reversed()))
        assertEquals(listOf(groupsio.id, discord.id), store.find(n.id)!!.deliveries.map { it.instance })
    }

    @Test
    fun `a pattern schedule comes back as saved`() {
        val n = notification().copy(
            schedule = Schedule(ScheduleKind.PATTERN, ZoneId.of("America/New_York"), pattern = "every 2 months on the 3rd tuesday at 18:00 starting 2027-02-01"),
        )
        store.save(n)

        assertEquals(n.schedule, store.find(n.id)!!.schedule)
    }

    @Test
    fun `lists are limited to the groups asked for`() {
        store.save(notification("notify_club"))
        store.save(notification("notify_family"))

        assertEquals(2, store.list(null).size)
        assertEquals(listOf("notify_family"), store.list(setOf("notify_family")).map { it.group })
        assertEquals(emptyList(), store.list(emptySet()))
        assertEquals(setOf("notify_club", "notify_family"), store.groups())
    }

    @Test
    fun `due notifications are moved on only if still due when expected`() {
        val n = notification()
        store.save(n)
        assertEquals(listOf(n.id), store.due(now, 10).map { it.id })

        assertTrue(store.advance(n.id, n.nextRunAt!!, now.plusSeconds(3600), NotificationStatus.ACTIVE, now))
        assertFalse(store.advance(n.id, n.nextRunAt!!, null, NotificationStatus.COMPLETED, now), "a second run of the same time loses")
        assertTrue(store.due(now, 10).isEmpty())
    }

    @Test
    fun `deleting takes the deliveries with it`() {
        val n = notification()
        store.save(n)

        assertTrue(store.delete(n.id))
        assertNull(store.find(n.id))
        assertEquals(0L, PostgresTestDatabase.jdbc.sql("SELECT count(*) FROM deliveries").query(Long::class.java).single())
    }
}
