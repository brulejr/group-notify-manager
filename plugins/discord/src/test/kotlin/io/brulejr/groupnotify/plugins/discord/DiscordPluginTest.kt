/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.discord

import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.testing.FakePluginContext
import io.brulejr.groupnotify.plugin.testing.StubHttpServer
import io.brulejr.groupnotify.plugin.testing.TestMessages
import tools.jackson.databind.json.JsonMapper
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiscordPluginTest {

    private val server = StubHttpServer(body = """{"id":"112233"}""")
    private val plugin = DiscordPlugin().apply { start(FakePluginContext(mapOf("allowed-hosts" to "127.0.0.1"))) }
    private val webhook = "${server.baseUrl}/api/webhooks/987654/secret-token"
    private val json = JsonMapper.builder().build()

    @AfterTest
    fun stop() = server.close()

    @Test
    fun `posts an embed and asks Discord to wait for the message id`() {
        val result = plugin.deliver(TestMessages.meeting(), DeliveryTarget(mapOf("webhookUrl" to webhook)))

        assertEquals(DeliveryResult.Delivered("posted", "112233"), result)
        val request = server.requests.single()
        assertEquals("/api/webhooks/987654/secret-token", request.path)
        assertEquals("wait=true", request.query)
        val body = json.readTree(request.body)
        assertEquals("Meeting reminder", body["embeds"][0]["title"].asString())
        assertEquals("club_members", body["embeds"][0]["footer"]["text"].asString())
        assertTrue(body["allowed_mentions"]["parse"].isEmpty, "nothing pings unless asked")
    }

    @Test
    fun `a plain post carries the subject and body as content, cut to Discord's limit`() {
        val long = "x".repeat(3000)
        plugin.deliver(TestMessages.meeting(long), DeliveryTarget(mapOf("webhookUrl" to webhook, "style" to "plain", "mention" to "@here")))

        val content = json.readTree(server.requests.single().body)["content"].asString()
        assertTrue(content.startsWith("@here\n\n**Meeting reminder**"))
        assertEquals(DiscordPayload.CONTENT_LIMIT, content.length)
    }

    @Test
    fun `a role mention is allowed to ping that role only`() {
        plugin.deliver(TestMessages.meeting(), DeliveryTarget(mapOf("webhookUrl" to webhook, "mention" to "<@&123456789012>")))

        val body = json.readTree(server.requests.single().body)
        assertEquals("<@&123456789012>", body["content"].asString())
        assertEquals("123456789012", body["allowed_mentions"]["roles"][0].asString())
    }

    @Test
    fun `server errors and rate limits are worth retrying, a deleted webhook is not`() {
        server.status = 503
        assertEquals(true, assertIs<DeliveryResult.Failed>(plugin.deliver(TestMessages.meeting(), DeliveryTarget(mapOf("webhookUrl" to webhook)))).retryable)
        server.status = 429
        assertEquals(true, assertIs<DeliveryResult.Failed>(plugin.deliver(TestMessages.meeting(), DeliveryTarget(mapOf("webhookUrl" to webhook)))).retryable)
        server.status = 404
        assertEquals(false, assertIs<DeliveryResult.Failed>(plugin.deliver(TestMessages.meeting(), DeliveryTarget(mapOf("webhookUrl" to webhook)))).retryable)
    }

    @Test
    fun `only Discord's own hosts are accepted by default`() {
        val stock = DiscordPlugin().apply { start(FakePluginContext()) }

        assertEquals(emptyList(), stock.validate(DeliveryTarget(mapOf("webhookUrl" to "https://discord.com/api/webhooks/1/abc"))))
        assertEquals("webhookUrl", stock.validate(DeliveryTarget(mapOf("webhookUrl" to "https://evil.example/api/webhooks/1/abc"))).single().field)
        assertEquals("mention", stock.validate(DeliveryTarget(mapOf("webhookUrl" to "https://discord.com/api/webhooks/1/abc", "mention" to "bob"))).single().field)
    }

    @Test
    fun `the summary names the webhook but never its token`() {
        val summary = plugin.summarize(DeliveryTarget(mapOf("webhookUrl" to webhook, "username" to "Club bot")))

        assertEquals("webhook 987654 as Club bot", summary)
    }
}
