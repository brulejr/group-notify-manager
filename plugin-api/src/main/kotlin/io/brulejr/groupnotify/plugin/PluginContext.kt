/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

import java.net.http.HttpClient

/**
 * What the application lends a plugin. Handed to [DeliveryPlugin.start] once.
 *
 * Plugins get HTTP, JSON and mail from here rather than bringing libraries of their own,
 * so a plugin jar stays small and cannot clash with the application's versions.
 */
interface PluginContext {

    /** This installation's settings for the plugin. */
    val settings: PluginSettings

    /** Shared, with the application's connect timeout. Set a request timeout per request. */
    val http: HttpClient

    /** The application's SMTP connection, or null when none is configured. */
    val mailer: Mailer?

    val log: PluginLog

    /** Maps, lists, strings, numbers, booleans and null, as JSON text. */
    fun toJson(value: Any?): String

    /** JSON text as maps, lists, strings, numbers, booleans and null. */
    fun parseJson(text: String): Any?
}
