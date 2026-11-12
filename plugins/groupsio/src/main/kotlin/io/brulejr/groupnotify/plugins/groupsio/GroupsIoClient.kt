/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.groupsio

import io.brulejr.groupnotify.plugin.PluginContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Calls to the groups.io REST API (`https://groups.io/api`), signed with an API key as a
 * Bearer token, so no login and no CSRF token are needed. Every call is a form-encoded
 * POST; most failures answer 400 with `{"object": "error", "type": …, "extra": …}`.
 *
 * Throws [java.io.IOException] when groups.io does not answer.
 *
 * @param apiKey an instance's key: whose member posts.
 * @param baseUrl `https://groups.io`, or a group's own domain.
 */
class GroupsIoClient(private val context: PluginContext, apiKey: String, baseUrl: String, private val timeout: Duration) {

    private val authorization = "Bearer $apiKey"
    private val baseUrl = baseUrl.trimEnd('/')

    /** One answer: its status, and its body as a JSON object where it is one. */
    data class Answer(val status: Int, val json: Map<*, *>?) {
        val ok get() = status == 200 && json?.get("object") != "error"

        /** Worth trying again: groups.io was busy or broken, not refusing. */
        val transient get() = status == 429 || status >= 500

        /** What groups.io said was wrong, e.g. `bad_request: not subscribed`. */
        val problem: String get() {
            val type = json?.get("type")?.toString()
            val extra = json?.get("extra")?.toString()?.takeIf { it.isNotBlank() }
            return when {
                status == 429 -> "rate limited by groups.io"
                type != null -> listOfNotNull(type, extra).joinToString(": ")
                else -> "groups.io answered $status"
            }
        }
    }

    /** The group's page for one message, e.g. `https://groups.io/g/myclub/message/123`. */
    fun messageUrl(group: String, msgNum: String) = "$baseUrl/g/$group/message/$msgNum"

    /** A change: a form-encoded POST. */
    fun call(endpoint: String, form: Map<String, String>): Answer = send(
        HttpRequest.newBuilder(URI("$baseUrl/api/v1/$endpoint"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encode(form))),
    )

    /** A read: a GET with the parameters in the query string. */
    fun get(endpoint: String, query: Map<String, String>): Answer =
        send(HttpRequest.newBuilder(URI("$baseUrl/api/v1/$endpoint?${encode(query)}")).GET())

    private fun send(builder: HttpRequest.Builder): Answer {
        val request = builder.timeout(timeout).header("Authorization", authorization).build()
        val response = context.http.send(request, HttpResponse.BodyHandlers.ofString())
        val body = response.body().orEmpty()
        // A 429 or an unknown endpoint has no JSON body; check the status before decoding.
        val json = if (body.isBlank()) null else runCatching { context.parseJson(body) as? Map<*, *> }.getOrNull()
        return Answer(response.statusCode(), json)
    }

    private fun encode(form: Map<String, String>) =
        form.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
}
