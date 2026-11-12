/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.notification.NotificationResult
import io.brulejr.groupnotify.support.Fixtures
import io.brulejr.groupnotify.support.TestUsers.admin
import io.brulejr.groupnotify.support.TestUsers.alice
import io.brulejr.groupnotify.support.TestUsers.bob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceServiceTest {

    private val f = Fixtures()
    private val service = f.instances

    private fun draft(name: String = "Club channel", config: Map<String, String> = mapOf("channel" to "general", "token" to "s3cret"), group: String = "notify_club") =
        InstanceDraft(group, "recording", name, config)

    private fun problems(result: InstanceResult) = assertIs<InstanceResult.Invalid>(result).problems.associate { it.field to it.reason }

    @Test
    fun `only an admin provisions, changes or deletes an instance`() {
        assertIs<InstanceResult.Forbidden>(service.create(draft(), alice))
        val instance = f.club

        assertIs<InstanceResult.Forbidden>(service.update(instance.id, draft(name = "Mine"), alice))
        assertIs<InstanceResult.Forbidden>(service.delete(instance.id, alice))
        assertIs<InstanceResult.Saved>(service.update(instance.id, draft(name = "Renamed"), admin))
    }

    @Test
    fun `members see their group's instances, never another group's`() {
        val club = f.club
        val family = f.instance(group = "notify_family", name = "Family channel")

        assertEquals(listOf(club.id), service.list(alice).map { it.id })
        assertNull(service.find(family.id, alice))
        assertEquals(listOf(family.id), service.list(bob).map { it.id })
        assertEquals(2, service.list(admin).size)
    }

    @Test
    fun `secrets are sealed at rest, masked when shown, and opened only to deliver`() {
        val instance = f.club

        assertTrue(instance.config.getValue("token").startsWith(SecretCipher.PREFIX))
        assertEquals(DeliveryService.MASK, service.masked(instance)["token"])
        assertEquals("s3cret", service.target(instance, emptyMap())["token"])
        assertTrue("s3cret" !in service.summary(instance))
    }

    @Test
    fun `a secret left blank or masked when editing keeps the stored one, and a new one replaces it`() {
        val instance = f.club

        val blank = assertIs<InstanceResult.Saved>(service.update(instance.id, draft(config = mapOf("channel" to "news", "token" to "")), admin)).instance
        assertEquals("s3cret", service.target(blank, emptyMap())["token"])
        assertEquals("news", blank.config["channel"])
        val masked = assertIs<InstanceResult.Saved>(service.update(instance.id, draft(config = mapOf("channel" to "news", "token" to DeliveryService.MASK)), admin)).instance
        assertEquals("s3cret", service.target(masked, emptyMap())["token"])
        val replaced = assertIs<InstanceResult.Saved>(service.update(instance.id, draft(config = mapOf("channel" to "news", "token" to "n3w")), admin)).instance
        assertEquals("n3w", service.target(replaced, emptyMap())["token"])

        val history = f.audit.history(admin, AuditQuery(groups = setOf("notify_club"))).map { it.detail }
        assertTrue("changed Channel" in history && "changed Token (secret)" in history, history.toString())
        assertTrue(history.none { "s3cret" in it || "n3w" in it })
    }

    @Test
    fun `a new instance given the mask has no secret at all`() {
        val created = assertIs<InstanceResult.Saved>(service.create(draft(config = mapOf("channel" to "general", "token" to DeliveryService.MASK)), admin)).instance

        assertNull(created.config["token"])
    }

    @Test
    fun `names are unique within a group, and the group, plugin and fields are checked`() {
        f.club

        assertEquals(mapOf("name" to "is already used by another instance in this group"), problems(service.create(draft(name = "club CHANNEL"), admin)))
        assertIs<InstanceResult.Saved>(service.create(draft(group = "notify_family"), admin))
        assertEquals(mapOf("config.channel" to "is required"), problems(service.create(draft(name = "Empty", config = emptyMap()), admin)))
        assertEquals(setOf("group", "plugin"), problems(service.create(InstanceDraft("homelab_users", "carrier-pigeon", "x"), admin)).keys)
    }

    @Test
    fun `the group and plugin are fixed once created`() {
        val instance = f.club

        assertEquals(setOf("group", "plugin"), problems(service.update(instance.id, InstanceDraft("notify_family", "log", "Club channel"), admin)).keys)
    }

    @Test
    fun `an instance still in use is not deleted`() {
        val instance = f.club
        assertIs<NotificationResult.Saved>(f.notifications.create(f.draft(), alice))

        assertEquals(InstanceResult.InUse(listOf("Meeting reminder")), service.delete(instance.id, admin))
        f.notificationStore.list(null).forEach { f.notificationStore.delete(it.id) }
        assertIs<InstanceResult.Saved>(service.delete(instance.id, admin))
    }

    @Test
    fun `overrides keep only overridable fields that differ, and the target applies only those`() {
        val instance = f.club

        assertEquals(mapOf("loud" to "true"), service.overrides(instance, mapOf("loud" to "true", "channel" to "elsewhere", "token" to "stolen")))
        assertEquals(emptyMap(), service.overrides(instance, mapOf("loud" to "false")))
        val target = service.target(instance, mapOf("loud" to "true", "channel" to "elsewhere"))
        assertEquals("general", target["channel"], "an override of an instance-only field is ignored even if stored")
        assertEquals("true", target["loud"])
    }
}
