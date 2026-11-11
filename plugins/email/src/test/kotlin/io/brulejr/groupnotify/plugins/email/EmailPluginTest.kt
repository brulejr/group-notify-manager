/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.email

import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.testing.FakePluginContext
import io.brulejr.groupnotify.plugin.testing.RecordingMailer
import io.brulejr.groupnotify.plugin.testing.TestMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmailPluginTest {

    private val mailer = RecordingMailer()
    private val plugin = EmailPlugin().apply { start(FakePluginContext(mailer = mailer)) }

    @Test
    fun `sends HTML with a text alternative to every recipient`() {
        val target = DeliveryTarget(mapOf("to" to "a@example.org, b@example.org", "bcc" to "c@example.org", "subjectPrefix" to "[Club]"))

        assertEquals(DeliveryResult.Delivered("3 recipients"), plugin.deliver(TestMessages.meeting(), target))
        val mail = mailer.sent.single()
        assertEquals(listOf("a@example.org", "b@example.org"), mail.to)
        assertEquals(listOf("c@example.org"), mail.bcc)
        assertEquals("[Club] Meeting reminder", mail.subject)
        assertTrue(mail.html!!.contains("<strong>"))
    }

    @Test
    fun `an SMTP failure is recorded and worth retrying`() {
        mailer.failWith = IllegalStateException("connection refused")

        val result = assertIs<DeliveryResult.Failed>(plugin.deliver(TestMessages.meeting(), DeliveryTarget(mapOf("to" to "a@example.org"))))
        assertEquals("SMTP: connection refused", result.reason)
        assertTrue(result.retryable)
    }

    @Test
    fun `without an SMTP server the plugin is not ready`() {
        assertFalse(EmailPlugin().apply { start(FakePluginContext()) }.status().ready)
    }

    @Test
    fun `addresses are checked loosely`() {
        val problems = plugin.validate(DeliveryTarget(mapOf("to" to "a@example.org, Jon Brule", "cc" to "nobody")))

        assertEquals(listOf("to", "cc"), problems.map { it.field })
    }
}
