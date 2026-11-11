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
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginSettings
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/** One call to Twilio's Messages resource per message. */
class TwilioClient(private val context: PluginContext, settings: PluginSettings) {

    private val accountSid = checkNotNull(settings["account-sid"])
    private val authorization = "Basic " + Base64.getEncoder().encodeToString(
        "${settings["api-key-sid"] ?: accountSid}:${checkNotNull(settings["auth-token"])}".toByteArray(),
    )
    private val from = settings["from"]
    private val messagingServiceSid = settings["messaging-service-sid"]
    private val baseUrl = settings.getOrDefault("api-base-url", "https://api.twilio.com").trimEnd('/')
    private val timeout = Duration.ofSeconds(settings["timeout"]?.toLongOrNull() ?: 10)

    fun send(to: String, body: String): DeliveryResult {
        val form = buildMap {
            put("To", to)
            put("Body", body)
            if (messagingServiceSid != null) put("MessagingServiceSid", messagingServiceSid) else put("From", checkNotNull(from))
        }.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
        val request = HttpRequest.newBuilder(URI("$baseUrl/2010-04-01/Accounts/$accountSid/Messages.json"))
            .timeout(timeout)
            .header("Authorization", authorization)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = try {
            context.http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            return DeliveryResult.Failed("Twilio did not answer: ${e.message ?: e.javaClass.simpleName}", retryable = true)
        }
        val json = runCatching { context.parseJson(response.body()) as? Map<*, *> }.getOrNull()
        return when (val status = response.statusCode()) {
            in 200..299 -> DeliveryResult.Delivered("queued", json?.get("sid")?.toString())
            429 -> DeliveryResult.Failed("rate limited by Twilio", retryable = true)
            in 500..599 -> DeliveryResult.Failed("Twilio answered $status", retryable = true)
            // Twilio's own codes say what is wrong: 21211 an invalid number, 21610 unsubscribed.
            else -> DeliveryResult.Failed("Twilio refused it ($status${json?.get("code")?.let { ", code $it" }.orEmpty()}): ${json?.get("message") ?: response.body().take(200)}")
        }
    }
}
