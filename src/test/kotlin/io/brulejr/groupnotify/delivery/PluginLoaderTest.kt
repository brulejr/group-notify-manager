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
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.testing.FakePluginContext
import io.brulejr.groupnotify.support.RecordingPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginLoaderTest {

    private fun loader(datafill: DeliveryDatafill = DeliveryDatafill()) = PluginLoader(datafill) { FakePluginContext() }

    private val classLoader = javaClass.classLoader

    @Test
    fun `the bundled plugins are found through ServiceLoader, each from its own jar`() {
        val handles = loader().load(listOf(LogPlugin()), classLoader)

        assertEquals(listOf("log", "discord", "email", "groupsio", "sms"), handles.map { it.id }.let { listOf(it.first()) + it.drop(1).sorted() })
        assertEquals("built in", handles.first().source)
    }

    @Test
    fun `a disabled plugin is not loaded`() {
        val handles = loader(DeliveryDatafill(disabled = setOf("sms", "log"))).load(listOf(LogPlugin()), classLoader)

        assertFalse(handles.any { it.id == "sms" || it.id == "log" })
    }

    @Test
    fun `two plugins with one id stop startup`() {
        assertFailsWith<IllegalStateException> { loader().load(listOf(RecordingPlugin("discord")), classLoader) }
    }

    @Test
    fun `a plugin that fails to start is loaded but not ready`() {
        val failing = object : DeliveryPlugin by RecordingPlugin("failing") {
            override fun start(context: PluginContext) = error("no config")
        }

        val handle = loader().load(listOf(failing), classLoader).single { it.id == "failing" }
        assertFalse(handle.status().ready)
        assertTrue("no config" in handle.status().reason)
    }
}
