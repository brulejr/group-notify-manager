/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.Mailer
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginLog
import io.brulejr.groupnotify.plugin.PluginSettings
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

/** What the application lends one plugin. */
class HostPluginContext(
    override val settings: PluginSettings,
    override val http: HttpClient,
    override val mailer: Mailer?,
    override val log: PluginLog,
    private val json: JsonMapper,
) : PluginContext {

    override fun toJson(value: Any?): String = json.writeValueAsString(value)

    override fun parseJson(text: String): Any? = json.readValue(text, Any::class.java)
}
