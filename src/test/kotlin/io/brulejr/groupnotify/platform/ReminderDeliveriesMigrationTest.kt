/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.platform

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.test.assertEquals

/**
 * V5 moved an event's deliveries onto its reminders. An event stored before it, with its
 * deliveries on the event, must come out with each delivery on every one of its
 * reminders, so it goes on sending where it did.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReminderDeliveriesMigrationTest {

    @Test
    fun `an event's deliveries are copied onto each of its reminders`() {
        val schema = "v5_" + UUID.randomUUID().toString().replace("-", "").take(8)
        val base = PostgresTestDatabase.dataSource
        val source = DriverManagerDataSource(base.url!!.replace("currentSchema=${PostgresTestDatabase.SCHEMA}", "currentSchema=$schema"), base.username!!, base.password!!)
        fun flyway(target: String) = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target(target).load()
        val jdbc = JdbcClient.create(source)

        flyway("4").migrate()
        val instance = UUID.randomUUID()
        val event = UUID.randomUUID()
        val (dayBefore, hourBefore) = UUID.randomUUID() to UUID.randomUUID()
        jdbc.sql(
            "INSERT INTO plugin_instances (id, group_name, plugin, name, config, created_by, created_at, updated_by, updated_at) " +
                "VALUES (:id, 'notify_club', 'log', 'Club log', '{}', 'a', now(), 'a', now())",
        ).param("id", instance).update()
        jdbc.sql(
            """
            INSERT INTO events (id, group_name, title, subject, body, body_format, schedule_kind, zone, reminders, status,
                                created_by, created_at, updated_by, updated_at)
            VALUES (:id, 'notify_club', 'Meeting', 's', 'b', 'MARKDOWN', 'ONCE', 'UTC',
                    CAST(:reminders AS jsonb), 'ACTIVE', 'a', now(), 'a', now())
            """,
        )
            .param("id", event)
            .param("reminders", """[{"id": "$dayBefore", "amount": 1, "unit": "DAYS", "timing": "BEFORE"}, {"id": "$hourBefore", "amount": 1, "unit": "HOURS", "timing": "BEFORE"}]""")
            .update()
        jdbc.sql(
            "INSERT INTO event_deliveries (id, event_id, position, instance_id, enabled, overrides) " +
                "VALUES (:id, :event, 0, :instance, true, '{\"body\": \"text\"}')",
        ).param("id", UUID.randomUUID()).param("event", event).param("instance", instance).update()

        flyway("latest").migrate()

        val rows = jdbc.sql("SELECT reminder_id, instance_id, overrides::text FROM event_deliveries ORDER BY reminder_id")
            .query { rs, _ -> Triple(rs.getObject(1, UUID::class.java), rs.getObject(2, UUID::class.java), rs.getString(3)) }
            .list()
        assertEquals(setOf(dayBefore, hourBefore), rows.map { it.first }.toSet())
        assertEquals(listOf(instance, instance), rows.map { it.second })
        assertEquals(listOf("{\"body\": \"text\"}", "{\"body\": \"text\"}"), rows.map { it.third })
    }
}
