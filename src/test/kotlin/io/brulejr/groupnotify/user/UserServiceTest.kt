/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import io.brulejr.groupnotify.audit.AuditDatafill
import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.audit.InMemoryAuditStore
import io.brulejr.groupnotify.group.GroupDatafill
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.platform.StoreType
import io.brulejr.groupnotify.support.TestClock
import io.brulejr.groupnotify.support.TestUsers.admin
import io.brulejr.groupnotify.support.TestUsers.alice
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServiceTest {

    private val clock = TestClock()
    private val store = InMemoryUserStore()
    private val audit = AuditService(InMemoryAuditStore(), GroupService(GroupDatafill(pattern = "notify_.*")), AuditDatafill(store = StoreType.MEMORY), clock)
    private val users = UserService(store, audit, UserDatafill(store = StoreType.MEMORY, lldapUrl = "https://lldap.example.org/"), clock)

    private fun seen(address: String? = "10.0.0.5", agent: String? = "Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0") = users.seen(alice, address, agent)

    @Test
    fun `a first request records the person as Authelia describes them, with a visit`() {
        seen()

        val r = store.find("alice")!!
        assertEquals("Alice", r.displayName)
        assertEquals(setOf("notify_club", "homelab_users"), r.groups)
        assertEquals(1, r.visits)
        assertEquals(clock.now, r.firstSeen)
        assertEquals("10.0.0.5", r.lastAddress)
    }

    @Test
    fun `the record is written at most every touch interval, sooner when something about them changes`() {
        seen()
        val first = clock.now

        clock.advance(Duration.ofMinutes(2))
        seen()
        assertEquals(first, store.find("alice")!!.lastSeen, "not written two minutes on")

        clock.advance(Duration.ofMinutes(4))
        seen()
        assertEquals(clock.now, store.find("alice")!!.lastSeen, "written once the touch interval has passed")

        clock.advance(Duration.ofMinutes(1))
        seen(address = "10.0.0.9")
        assertEquals("10.0.0.9", store.find("alice")!!.lastAddress, "a new address is written at once")
    }

    @Test
    fun `a gap longer than the visit gap starts a new visit`() {
        seen()
        clock.advance(Duration.ofMinutes(20))
        seen()
        assertEquals(1, store.find("alice")!!.visits)

        clock.advance(Duration.ofMinutes(45))
        seen()
        val r = store.find("alice")!!
        assertEquals(2, r.visits)
        assertEquals(clock.now, r.visitStartedAt)
    }

    @Test
    fun `only admins see anyone, with what they did, and may forget them`() {
        seen()
        audit.record(AuditEvent(occurredAt = clock.now, type = AuditEventType.CREATED, actor = "alice", notificationId = null, notificationTitle = "Net", group = "notify_club"))

        assertTrue(users.list(alice).isEmpty())
        assertNull(users.find(alice, "alice"))
        assertTrue(users.recentActions(alice, "alice").isEmpty())
        assertFalse(users.forget(alice, "alice"))

        assertEquals(listOf("alice"), users.list(admin).map { it.username })
        assertEquals(1, users.activity(admin)["alice"]?.events)
        assertEquals(listOf("Net"), users.recentActions(admin, "alice").map { it.notificationTitle })
        assertEquals("https://lldap.example.org/user/alice", users.lldapLink("alice"))

        assertTrue(users.forget(admin, "alice"))
        assertNull(store.find("alice"))
        // Coming back, they reappear as new.
        seen()
        assertEquals(1, store.find("alice")!!.visits)
    }

    @Test
    fun `a client is put in a few words`() {
        assertEquals("Firefox on Linux", io.brulejr.groupnotify.user.web.Browsers.describe("Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0"))
        assertEquals("Chrome on Android", io.brulejr.groupnotify.user.web.Browsers.describe("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"))
        assertEquals("curl", io.brulejr.groupnotify.user.web.Browsers.describe("curl/8.9.1"))
        assertNull(io.brulejr.groupnotify.user.web.Browsers.describe(null))
    }
}
