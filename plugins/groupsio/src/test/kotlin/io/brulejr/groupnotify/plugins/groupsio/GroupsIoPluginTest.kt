/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.groupsio

import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.testing.FakePluginContext
import io.brulejr.groupnotify.plugin.testing.RecordingMailer
import io.brulejr.groupnotify.plugin.testing.TestMessages
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupsIoPluginTest {

    private val mailer = RecordingMailer()
    private val plugin = GroupsIoPlugin().apply { start(FakePluginContext(mapOf("from" to "member@example.org"), mailer)) }

    @Test
    fun `posts to the subgroup's address with hashtags leading the subject`() {
        val target = DeliveryTarget(mapOf("group" to "myclub+events", "hashtags" to "announcement, #meeting"))

        assertEquals(DeliveryResult.Delivered("posted to myclub+events@groups.io"), plugin.deliver(TestMessages.meeting(), target))
        val mail = mailer.sent.single()
        assertEquals(listOf("myclub+events@groups.io"), mail.to)
        assertEquals("#announcement #meeting Meeting reminder", mail.subject)
        assertEquals("member@example.org", mail.from)
    }

    @Test
    fun `a full address or another domain is used as given`() {
        assertEquals("club@lists.example.org", plugin.address("club@lists.example.org"))
        val own = GroupsIoPlugin().apply { start(FakePluginContext(mapOf("domain" to "groups.example.org"), mailer)) }
        assertEquals("club@groups.example.org", own.address("club"))
    }

    @Test
    fun `group names and hashtags are checked`() {
        val problems = plugin.validate(DeliveryTarget(mapOf("group" to "my club", "hashtags" to "#ok, #not!ok")))

        assertEquals(listOf("group", "hashtags"), problems.map { it.field })
    }
}
