/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.sms

import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.testing.FakePluginContext
import io.brulejr.groupnotify.plugin.testing.StubHttpServer
import io.brulejr.groupnotify.plugin.testing.TestMessages
import java.net.URLDecoder
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SmsPluginTest {

    private val server = StubHttpServer(status = 201, body = """{"sid":"SM123","status":"queued"}""")

    private fun plugin(vararg settings: Pair<String, String>) = SmsPlugin().apply {
        start(
            FakePluginContext(
                mapOf("account-sid" to "AC1", "auth-token" to "token", "from" to "+15550001111", "api-base-url" to server.baseUrl) + settings,
            ),
        )
    }

    @AfterTest
    fun stop() = server.close()

    private fun form(body: String) = body.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }

    @Test
    fun `texts each number separately through Twilio's Messages resource`() {
        val result = plugin().deliver(TestMessages.meeting(), DeliveryTarget(mapOf("to" to "+15551234567, +15557654321", "prefix" to "[Club]")))

        assertEquals(DeliveryResult.Delivered("2 of 2 numbers", "SM123,SM123"), result)
        assertEquals(2, server.requests.size)
        val first = server.requests.first()
        assertEquals("/2010-04-01/Accounts/AC1/Messages.json", first.path)
        assertEquals("Basic " + Base64.getEncoder().encodeToString("AC1:token".toByteArray()), first.headers["authorization"])
        val fields = form(first.body)
        assertEquals("+15551234567", fields["To"])
        assertEquals("+15550001111", fields["From"])
        assertEquals("[Club] Meeting reminder\nThe monthly meeting is on Tuesday.", fields["Body"])
    }

    @Test
    fun `a messaging service is used in place of a from number when set`() {
        plugin("messaging-service-sid" to "MG9").deliver(TestMessages.meeting(), DeliveryTarget(mapOf("to" to "+15551234567")))

        val fields = form(server.requests.single().body)
        assertEquals("MG9", fields["MessagingServiceSid"])
        assertFalse("From" in fields)
    }

    @Test
    fun `long text is cut to the configured length`() {
        val text = SmsText.compose(TestMessages.meeting().copy(text = "y".repeat(500)), null, includeSubject = false, maxLength = 160)

        assertEquals(160, text.length)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun `without credentials the plugin loads but is not ready`() {
        val unconfigured = SmsPlugin().apply { start(FakePluginContext()) }

        assertFalse(unconfigured.status().ready)
        assertTrue("account-sid" in unconfigured.status().reason)
        assertIs<DeliveryResult.Failed>(unconfigured.deliver(TestMessages.meeting(), DeliveryTarget(mapOf("to" to "+15551234567"))))
    }

    @Test
    fun `a refusal reports Twilio's own code and is not retried`() {
        server.status = 400
        server.body = """{"code":21211,"message":"Invalid 'To' Phone Number"}"""

        val result = assertIs<DeliveryResult.Failed>(plugin().deliver(TestMessages.meeting(), DeliveryTarget(mapOf("to" to "+15551234567"))))
        assertTrue("code 21211" in result.reason)
        assertFalse(result.retryable)
    }

    @Test
    fun `numbers must be in E164 form and are masked in summaries`() {
        val sms = plugin()

        assertEquals(listOf("555-1234"), sms.validate(DeliveryTarget(mapOf("to" to "+15551234567, 555-1234"))).map { it.reason.substringBefore(' ') })
        assertEquals("+1•••4567", sms.summarize(DeliveryTarget(mapOf("to" to "+15551234567"))))
        assertEquals("2 numbers", sms.summarize(DeliveryTarget(mapOf("to" to "+15551234567,+15557654321"))))
    }
}
