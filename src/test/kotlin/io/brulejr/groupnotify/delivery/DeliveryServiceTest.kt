/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldOption
import io.brulejr.groupnotify.plugin.FieldType
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginField
import io.brulejr.groupnotify.plugin.PluginStatus
import io.brulejr.groupnotify.plugin.testing.TestMessages
import io.brulejr.groupnotify.support.RecordingPlugin
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeliveryServiceTest {

    private val waits = mutableListOf<Duration>()

    private fun service(vararg plugins: DeliveryPlugin, maxAttempts: Int = 3) = DeliveryService(
        plugins.map { PluginHandle(it, "test") },
        DeliveryDatafill(maxAttempts = maxAttempts, retryBackoff = Duration.ofSeconds(2)),
    ) { waits += it }

    @Test
    fun `retryable failures are retried with a doubling backoff, up to the limit`() {
        val plugin = RecordingPlugin("p", DeliveryResult.Failed("busy", retryable = true))
        val outcome = service(plugin).deliver("p", TestMessages.meeting(), mapOf("channel" to "x"))

        assertEquals(3, outcome.attempts)
        assertEquals(listOf(Duration.ofSeconds(2), Duration.ofSeconds(4)), waits)
        assertEquals(DeliveryResult.Failed("busy", retryable = true), outcome.result)
    }

    @Test
    fun `a failure that is not retryable is tried once`() {
        val plugin = RecordingPlugin("p", DeliveryResult.Failed("no such channel"))

        assertEquals(1, service(plugin).deliver("p", TestMessages.meeting(), emptyMap()).attempts)
    }

    @Test
    fun `a plugin that throws is recorded as a failure`() {
        val throwing = object : DeliveryPlugin by RecordingPlugin("boom") {
            override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult = error("kaboom")
        }

        val result = assertIs<DeliveryResult.Failed>(service(throwing).deliver("boom", TestMessages.meeting(), emptyMap()).result)
        assertTrue("kaboom" in result.reason)
    }

    @Test
    fun `a plugin that is not ready fails without being called`() {
        val plugin = RecordingPlugin("p")
        val unready = object : DeliveryPlugin by plugin {
            override fun status() = PluginStatus.notReady("missing token")
        }

        val result = assertIs<DeliveryResult.Failed>(service(unready).deliver("p", TestMessages.meeting(), emptyMap()).result)
        assertEquals("Recording is not ready: missing token", result.reason)
        assertTrue(plugin.sent.isEmpty())
    }

    @Test
    fun `targets are checked against the plugin's fields before the plugin is asked`() {
        val plugin = object : DeliveryPlugin by RecordingPlugin("p") {
            override val fields = listOf(
                PluginField("channel", "Channel", required = true),
                PluginField("style", "Style", FieldType.SELECT, options = listOf(FieldOption("a"), FieldOption("b"))),
                PluginField("hook", "Hook", FieldType.URL),
                PluginField("count", "Count", FieldType.NUMBER),
            )
        }
        val service = service(plugin)

        assertEquals(
            listOf("channel", "style", "hook", "count"),
            service.validate("p", mapOf("style" to "c", "hook" to "ftp://x", "count" to "many")).map { it.field },
        )
        assertEquals("plugin", service.validate("nope", emptyMap()).single().field)
    }

    @Test
    fun `secrets are masked for showing, and only known fields are kept`() {
        val service = service(RecordingPlugin("p"))

        assertEquals(mapOf("channel" to "x", "token" to DeliveryService.MASK), service.mask("p", mapOf("channel" to "x", "token" to "abc")))
        assertEquals(mapOf("channel" to "x", "loud" to "true"), service.normalize("p", mapOf("channel" to " x ", "loud" to "on", "junk" to "1")))
    }
}
