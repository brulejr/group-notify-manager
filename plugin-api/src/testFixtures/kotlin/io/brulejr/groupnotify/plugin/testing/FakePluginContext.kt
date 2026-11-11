/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin.testing

import io.brulejr.groupnotify.plugin.Mailer
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginLog
import io.brulejr.groupnotify.plugin.PluginSettings
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

/** A [PluginContext] for tests: real HTTP and JSON, a mailer of the test's choosing, a log kept in memory. */
class FakePluginContext(
    settings: Map<String, String> = emptyMap(),
    override val mailer: Mailer? = null,
) : PluginContext {

    private val json = JsonMapper.builder().build()

    override val settings = PluginSettings(settings)

    override val http: HttpClient = HttpClient.newHttpClient()

    val logged = mutableListOf<String>()

    override val log = object : PluginLog {
        override fun debug(message: String) { logged += "DEBUG $message" }
        override fun info(message: String) { logged += "INFO $message" }
        override fun warn(message: String, error: Throwable?) { logged += "WARN $message" }
        override fun error(message: String, error: Throwable?) { logged += "ERROR $message" }
    }

    override fun toJson(value: Any?): String = json.writeValueAsString(value)

    override fun parseJson(text: String): Any? = json.readValue(text, Any::class.java)
}
