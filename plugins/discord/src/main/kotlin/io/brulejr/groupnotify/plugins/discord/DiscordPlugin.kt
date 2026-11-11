/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.discord

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldOption
import io.brulejr.groupnotify.plugin.FieldProblem
import io.brulejr.groupnotify.plugin.FieldType
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginField
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Posts to a Discord channel through one of its webhooks: Server Settings → Integrations
 * → Webhooks, then Copy Webhook URL. No bot and no installation settings are needed; the
 * webhook URL carries its own credential, so it is kept as a secret.
 *
 * Settings (`notify.delivery.settings.discord.*`), all optional:
 * - `allowed-hosts`: webhook hosts accepted, comma-separated. Defaults to Discord's own.
 * - `timeout`: seconds to wait for Discord, default 10.
 */
class DiscordPlugin : DeliveryPlugin {

    private lateinit var context: PluginContext
    private var allowedHosts = DEFAULT_HOSTS
    private var timeout = Duration.ofSeconds(10)

    override val id = "discord"
    override val name = "Discord"
    override val description = "Posts to a Discord channel through a channel webhook."
    override val icon = "discord"

    override val fields = listOf(
        PluginField(
            "webhookUrl", "Webhook URL", FieldType.URL, required = true, secret = true,
            placeholder = "https://discord.com/api/webhooks/…",
            help = "Channel settings → Integrations → Webhooks → Copy Webhook URL.",
        ),
        PluginField(
            "style", "Style", FieldType.SELECT,
            options = listOf(FieldOption("embed", "Embed card"), FieldOption("plain", "Plain message")),
            defaultValue = "embed",
        ),
        PluginField("username", "Post as", placeholder = "the webhook's own name", help = "Overrides the name shown on the post."),
        PluginField("avatarUrl", "Avatar URL", FieldType.URL),
        PluginField(
            "mention", "Mention", placeholder = "@everyone, @here or a role id",
            help = "Pings before the post. A role is its numeric id (Developer Mode → Copy Role ID).",
        ),
    )

    override fun start(context: PluginContext) {
        this.context = context
        context.settings["allowed-hosts"]?.let { hosts ->
            allowedHosts = hosts.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        }
        context.settings["timeout"]?.toLongOrNull()?.let { timeout = Duration.ofSeconds(it) }
    }

    override fun validate(target: DeliveryTarget): List<FieldProblem> = buildList {
        target["webhookUrl"]?.let { url ->
            val uri = runCatching { URI(url) }.getOrNull()
            when {
                uri?.host == null -> add(FieldProblem("webhookUrl", "is not a URL"))
                uri.host.lowercase() !in allowedHosts -> add(FieldProblem("webhookUrl", "is not a Discord webhook (${uri.host})"))
                !uri.path.contains("/api/webhooks/") -> add(FieldProblem("webhookUrl", "is not a webhook URL; it has no /api/webhooks/"))
            }
        }
        target["mention"]?.let { if (Mention.parse(it) == null) add(FieldProblem("mention", "must be @everyone, @here or a numeric role id")) }
    }

    override fun summarize(target: DeliveryTarget): String {
        // /api/webhooks/{id}/{token}: the id is harmless, the token is the secret.
        val webhookId = target["webhookUrl"]?.substringAfter("/api/webhooks/", "")?.substringBefore('/').orEmpty()
        val who = target["username"]?.let { " as $it" }.orEmpty()
        return (if (webhookId.isEmpty()) "webhook" else "webhook $webhookId") + who
    }

    override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult {
        val mention = target["mention"]?.let(Mention::parse)
        val body = DiscordPayload.build(message, target, mention)
        val url = target.require("webhookUrl").let { if ('?' in it) "$it&wait=true" else "$it?wait=true" }
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(context.toJson(body)))
            .build()
        val response = try {
            context.http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            return DeliveryResult.Failed("Discord did not answer: ${e.message ?: e.javaClass.simpleName}", retryable = true)
        }
        return when (val status = response.statusCode()) {
            in 200..299 -> DeliveryResult.Delivered("posted", messageId(response.body()))
            429 -> DeliveryResult.Failed("rate limited by Discord", retryable = true)
            in 500..599 -> DeliveryResult.Failed("Discord answered $status", retryable = true)
            401, 403, 404 -> DeliveryResult.Failed("Discord refused the webhook ($status): it may have been deleted")
            else -> DeliveryResult.Failed("Discord answered $status: ${errorMessage(response.body())}")
        }
    }

    private fun messageId(body: String): String? =
        (runCatching { context.parseJson(body) }.getOrNull() as? Map<*, *>)?.get("id")?.toString()

    private fun errorMessage(body: String): String =
        (runCatching { context.parseJson(body) }.getOrNull() as? Map<*, *>)?.get("message")?.toString() ?: body.take(200)

    private companion object {
        val DEFAULT_HOSTS = setOf("discord.com", "discordapp.com", "canary.discord.com", "ptb.discord.com")
    }
}
