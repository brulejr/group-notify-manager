/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit.postgres

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.DispatchTrigger
import io.brulejr.groupnotify.platform.PostgresTestDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals

@Testcontainers(disabledWithoutDocker = true)
class PostgresAuditStoreTest {

    private val store = PostgresAuditStore(PostgresTestDatabase.jdbc)
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun event(type: AuditEventType, group: String = "notify_club", at: Instant = now) = AuditEvent(
        occurredAt = at, type = type, actor = "scheduler", notificationId = UUID.randomUUID(), notificationTitle = "t",
        group = group, plugin = "sms", target = "+1•••4567", trigger = DispatchTrigger.SCHEDULE, detail = "ok", attempts = 1,
    )

    @BeforeEach
    fun reset() = PostgresTestDatabase.reset()

    @Test
    fun `events come back newest first, filtered and paged`() {
        val first = store.record(event(AuditEventType.SENT))
        val second = store.record(event(AuditEventType.FAILED))
        store.record(event(AuditEventType.SENT, "notify_family"))

        assertEquals(listOf(second, first), store.find(AuditQuery(groups = setOf("notify_club"))))
        assertEquals(listOf(first), store.find(AuditQuery(groups = setOf("notify_club"), beforeId = second.id)))
        assertEquals(listOf(second), store.find(AuditQuery(types = setOf(AuditEventType.FAILED))))
        assertEquals(mapOf(AuditEventType.SENT to 2L, AuditEventType.FAILED to 1L), store.count(AuditQuery()))
        assertEquals(emptyList(), store.find(AuditQuery(groups = emptySet())))
    }

    @Test
    fun `old events are purged`() {
        store.record(event(AuditEventType.SENT, at = now.minus(400, ChronoUnit.DAYS)))
        store.record(event(AuditEventType.SENT))

        assertEquals(1, store.purge(now.minus(365, ChronoUnit.DAYS)))
        assertEquals(1, store.find(AuditQuery()).size)
    }
}
