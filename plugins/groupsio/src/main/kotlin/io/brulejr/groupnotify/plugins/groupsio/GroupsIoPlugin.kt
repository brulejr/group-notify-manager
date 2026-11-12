/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.groupsio

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldOption
import io.brulejr.groupnotify.plugin.FieldProblem
import io.brulejr.groupnotify.plugin.FieldType
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginField
import io.brulejr.groupnotify.plugin.PluginLog
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Posts a new topic to a groups.io group through the groups.io REST API, as the member
 * who owns the API key: a draft is created (`newdraft`), given the subject and HTML body
 * (`updatedraft`), and posted (`postdraft`). Everything used is on groups.io's free plan.
 *
 * That member must be subscribed with permission to post. A moderated group holds the
 * post for approval unless they may post unmoderated; groups.io says so, and the
 * delivery records it. Hashtags lead the subject, which is where groups.io reads them.
 *
 * Once posted, the message is looked up in the archive (`getmessages`), which `postdraft`
 * does not return, for a link to it and for the topic it started, on which any
 * [TopicSettings] are then set (`updatetopic`). Neither step can undo the post, so their
 * failures are reported in the delivery's detail rather than failing it.
 *
 * A failure before `postdraft` is safe to retry. One during it, a timeout or a server
 * error, leaves unknown whether the post went out, so it is not retried: a second
 * attempt could post twice.
 *
 * An instance holds the group, the API key (from https://groups.io/settings/apikeys) and,
 * for a group on its own domain, the API's address; a notification can override only
 * how it posts: hashtags, special notice, the topic settings.
 *
 * Settings (`notify.delivery.settings.groupsio.*`):
 * - `timeout`: seconds to wait for each call, default 15.
 * - `lookup-attempts`, `lookup-delay-ms`: how often, and how far apart, to look for the
 *   new post in the archive, which can lag the post a little; default 4 and 1500.
 */
class GroupsIoPlugin : DeliveryPlugin {

    private lateinit var context: PluginContext
    private var log: PluginLog? = null
    private var timeout = Duration.ofSeconds(15)
    private var lookupAttempts = 4
    private var lookupDelay = Duration.ofMillis(1500)

    override val id = "groupsio"
    override val name = "groups.io"
    override val description = "Posts a new topic to a groups.io group through its API."
    override val icon = "people"

    override val fields = listOf(
        PluginField(
            "group", "Group", required = true, placeholder = "e.g. myclub",
            help = "The group's name, as in groups.io/g/<name>.",
        ),
        PluginField(
            "apiKey", "API key", required = true, secret = true,
            help = "From groups.io → Account → API Keys (groups.io/settings/apikeys). Posts go out as that key's member.",
        ),
        PluginField(
            "hashtags", "Hashtags", placeholder = "e.g. #announcement, #meeting", overridable = true,
            help = "Added to the start of the subject. groups.io files the post under them.",
        ),
        PluginField(
            "special", "Special notice", FieldType.CHECKBOX, overridable = true,
            help = "Reaches every member, even those who get no email or digests. Moderators and owners only.",
        ),
        PluginField(
            "fromOwner", "Post as the group's owners", FieldType.CHECKBOX, overridable = true,
            help = "Sent from the group's owner address rather than yours. Moderators and owners only.",
        ),
        PluginField(
            "sticky", "Pin to the top", FieldType.CHECKBOX, overridable = true,
            help = "The topic stays at the top of the group's archive.",
        ),
        PluginField(
            "locked", "Lock the topic", FieldType.CHECKBOX, overridable = true,
            help = "Nobody can reply: for announcements.",
        ),
        PluginField(
            "replyTo", "Replies go", FieldType.SELECT, overridable = true,
            options = listOf(FieldOption("", "As the group's default")) + TopicSettings.REPLY_TO,
            help = "Where a reply to this topic is sent.",
        ),
        PluginField(
            "moderated", "Moderate replies", FieldType.CHECKBOX, overridable = true,
            help = "Replies to the topic wait for a moderator's approval.",
        ),
        PluginField(
            "apiBaseUrl", "API address", FieldType.URL, placeholder = DEFAULT_BASE_URL,
            help = "Only for a group on its own domain.",
        ),
    )

    override fun start(context: PluginContext) {
        val settings = context.settings
        this.context = context
        log = context.log
        timeout = settings["timeout"]?.toLongOrNull()?.let { Duration.ofSeconds(it.coerceIn(1, 120)) } ?: timeout
        lookupAttempts = settings["lookup-attempts"]?.toIntOrNull()?.coerceIn(1, 10) ?: lookupAttempts
        lookupDelay = settings["lookup-delay-ms"]?.toLongOrNull()?.let { Duration.ofMillis(it.coerceIn(0, 10_000)) } ?: lookupDelay
    }

    override fun validate(target: DeliveryTarget): List<FieldProblem> = buildList {
        target["group"]?.let { if (!GROUP.matches(it.trim())) add(FieldProblem("group", "must be a group name such as myclub")) }
        hashtags(target).filterNot { HASHTAG.matches(it) }.forEach { add(FieldProblem("hashtags", "$it is not a hashtag: letters, digits, _ and - only")) }
        target["replyTo"]?.let { value -> if (TopicSettings.REPLY_TO.none { it.value == value.trim() }) add(FieldProblem("replyTo", "is not one of groups.io's choices")) }
    }

    override fun summarize(target: DeliveryTarget): String =
        group(target) + hashtags(target).joinToString("") { " $it" }

    override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult {
        val apiKey = target["apiKey"] ?: return DeliveryResult.Failed("the instance has no API key")
        val client = GroupsIoClient(context, apiKey, target["apiBaseUrl"] ?: DEFAULT_BASE_URL, timeout)
        val group = group(target)
        val subject = (hashtags(target) + message.subject).joinToString(" ")
        val topic = TopicSettings.of(target)
        val started = Instant.now()

        val draft = try {
            client.call("newdraft", mapOf("group_name" to group, "draft_type" to "draft_type_post"))
        } catch (e: IOException) {
            return unanswered(e)
        }
        if (!draft.ok) return DeliveryResult.Failed("groups.io refused a draft in $group: ${draft.problem}", retryable = draft.transient)
        val draftId = draft.json?.get("id")?.toString()
            ?: return DeliveryResult.Failed("groups.io gave the draft no id")
        val author = draft.json["user_id"]?.toString()


        val update = buildMap {
            put("draft_id", draftId)
            put("subject", subject)
            put("body", message.html)
            if (target.flag("special")) put("is_special", "true")
        }
        val updated = try {
            client.call("updatedraft", update)
        } catch (e: IOException) {
            discard(client, draftId)
            return unanswered(e)
        }
        if (!updated.ok) {
            discard(client, draftId)
            return DeliveryResult.Failed("groups.io refused the message: ${updated.problem}", retryable = updated.transient)
        }

        val post = buildMap {
            put("draft_id", draftId)
            if (target.flag("fromOwner")) put("from_owner", "true")
        }
        val posted = try {
            client.call("postdraft", post)
        } catch (e: IOException) {
            return DeliveryResult.Failed("groups.io did not answer the post, so it may or may not have gone out; check the group before sending again (${e.message ?: e.javaClass.simpleName})")
        }
        return when {
            posted.ok && pending(posted) -> DeliveryResult.Delivered(
                "held for moderator approval in $group" + if (topic.any) "; topic settings not applied" else "",
            )
            posted.ok -> afterPosting(client, group, message.subject, author, started, topic)
            posted.status == 429 -> {
                // Turned away before anything was sent, so trying again is safe.
                discard(client, draftId)
                DeliveryResult.Failed(posted.problem, retryable = true)
            }
            posted.status >= 500 -> DeliveryResult.Failed("groups.io answered ${posted.status} to the post, so it may or may not have gone out; check the group before sending again")
            else -> {
                discard(client, draftId)
                DeliveryResult.Failed("groups.io refused the post: ${posted.problem}")
            }
        }
    }

    /** Finds the new post for its link, then sets its topic up; the post stands whatever happens here. */
    private fun afterPosting(
        client: GroupsIoClient, group: String, subject: String, author: String?, started: Instant, topic: TopicSettings,
    ): DeliveryResult {
        val found = find(client, group, subject, author, started)
            ?: return DeliveryResult.Delivered(
                "posted to $group" + if (topic.any) "; not found in the archive, so the topic settings were not applied" else "",
            )
        val link = found["msg_num"]?.toString()?.let { client.messageUrl(group, it) }
        if (!topic.any) return DeliveryResult.Delivered("posted to $group", link)

        val topicId = found["topic_id"]?.toString()
            ?: return DeliveryResult.Delivered("posted to $group; the post has no topic id, so the topic settings were not applied", link)
        val changed = try {
            client.call("updatetopic", mapOf("topic_id" to topicId) + topic.form())
        } catch (e: IOException) {
            return DeliveryResult.Delivered("posted to $group; groups.io did not answer, so the topic settings were not applied", link)
        }
        return if (changed.ok) {
            DeliveryResult.Delivered("posted to $group; ${topic.describe()}", link)
        } else {
            DeliveryResult.Delivered("posted to $group; topic settings refused: ${changed.problem}", link)
        }
    }

    /**
     * The newest message in the group by the key's member, posted since [started] (with a
     * little allowance for clocks), whose subject carries the notification's. The archive
     * can lag the post, so it is looked for a few times.
     */
    private fun find(client: GroupsIoClient, group: String, subject: String, author: String?, started: Instant): Map<*, *>? {
        val since = started.minus(CLOCK_ALLOWANCE)
        repeat(lookupAttempts) { attempt ->
            if (attempt > 0) Thread.sleep(lookupDelay)
            val answer = try {
                client.get("getmessages", mapOf("group_name" to group, "sort_field" to "Created", "sort_dir" to "desc", "limit" to "10"))
            } catch (e: IOException) {
                log?.warn("groups.io: could not read $group's messages: ${e.message}")
                return null
            }
            if (!answer.ok) {
                log?.warn("groups.io: could not read $group's messages: ${answer.problem}")
                return null
            }
            val match = (answer.json?.get("data") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().firstOrNull { m ->
                (author == null || m["user_id"]?.toString() == author) &&
                    m["subject"]?.toString()?.contains(subject, ignoreCase = true) == true &&
                    createdAt(m)?.let { !it.isBefore(since) } != false
            }
            if (match != null) return match
        }
        return null
    }

    private fun createdAt(message: Map<*, *>): Instant? =
        message["created"]?.toString()?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    /** Removes a draft that will not be posted, so it does not linger in the member's drafts. */
    private fun discard(client: GroupsIoClient, draftId: String) {
        runCatching { client.call("deletedraft", mapOf("draft_id" to draftId)) }
    }

    private fun unanswered(e: IOException) =
        DeliveryResult.Failed("groups.io did not answer: ${e.message ?: e.javaClass.simpleName}", retryable = true)

    /** groups.io answers a post that needs approval with a "pending post" object rather than "success". */
    private fun pending(answer: GroupsIoClient.Answer) =
        answer.json?.get("object")?.toString()?.contains("pending") == true

    private fun group(target: DeliveryTarget) = target.require("group").trim().lowercase()

    /** Each hashtag with its #, so `announcement` and `#announcement` are the same tag. */
    private fun hashtags(target: DeliveryTarget) = target.list("hashtags")
        .flatMap { it.split(' ') }.map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith('#')) it else "#$it" }

    private companion object {
        const val DEFAULT_BASE_URL = "https://groups.io"
        val GROUP = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        val HASHTAG = Regex("#[A-Za-z0-9_-]+")
        val CLOCK_ALLOWANCE: Duration = Duration.ofMinutes(5)
    }
}
