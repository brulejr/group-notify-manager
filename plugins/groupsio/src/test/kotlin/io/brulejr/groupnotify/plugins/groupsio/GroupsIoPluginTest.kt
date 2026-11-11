/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.groupsio

import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.testing.FakePluginContext
import io.brulejr.groupnotify.plugin.testing.StubHttpServer
import io.brulejr.groupnotify.plugin.testing.StubHttpServer.Response
import io.brulejr.groupnotify.plugin.testing.TestMessages
import java.net.URLDecoder
import java.time.OffsetDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupsIoPluginTest {

    private val server = StubHttpServer()
    private val plugin = GroupsIoPlugin().apply {
        start(
            FakePluginContext(
                mapOf("api-key" to "k3y", "api-base-url" to server.baseUrl, "timeout" to "2", "lookup-attempts" to "2", "lookup-delay-ms" to "0"),
            ),
        )
    }
    private val club = DeliveryTarget(mapOf("group" to "brulenet"))
    private val meeting = TestMessages.meeting()

    /** The archive's newest messages: the new post by member 7, then an older one by someone else. */
    private fun archive(subject: String = "#announcement ${meeting.subject}", author: Int = 7) = """
        {"object":"list","data":[
          {"id":900,"msg_num":321,"topic_id":555,"user_id":$author,"subject":"$subject","created":"${OffsetDateTime.now()}"},
          {"id":899,"msg_num":320,"topic_id":554,"user_id":8,"subject":"${meeting.subject}","created":"2020-01-01T00:00:00Z"}
        ]}
    """.trimIndent()

    /** Answers each call as groups.io does, with [post] for postdraft and [messages] for getmessages. */
    private fun groupsIo(
        post: Response = Response(200, """{"object":"success","extra":""}"""),
        messages: Response = Response(200, archive()),
        topic: Response = Response(200, """{"object":"topic","id":555}"""),
    ) {
        server.respond = { request ->
            when (request.path) {
                "/api/v1/newdraft", "/api/v1/updatedraft" -> Response(200, """{"object":"draft","id":42,"user_id":7}""")
                "/api/v1/postdraft" -> post
                "/api/v1/getmessages" -> messages
                "/api/v1/updatetopic" -> topic
                else -> Response(200, """{"object":"success"}""")
            }
        }
    }

    private fun decode(text: String?) = text.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
        val (k, v) = it.split('=', limit = 2)
        k to URLDecoder.decode(v, Charsets.UTF_8)
    }

    private fun form(path: String) = decode(server.requests.last { it.path == path }.body)

    @AfterTest
    fun stop() = server.close()

    @Test
    fun `posts a new topic as draft, update, post, signed with the API key, and links to it`() {
        groupsIo()
        val target = DeliveryTarget(mapOf("group" to "Brulenet", "hashtags" to "announcement", "special" to "true"))

        val result = plugin.deliver(meeting, target)

        assertEquals(DeliveryResult.Delivered("posted to brulenet", "${server.baseUrl}/g/brulenet/message/321"), result)
        assertEquals(
            listOf("/api/v1/newdraft", "/api/v1/updatedraft", "/api/v1/postdraft", "/api/v1/getmessages"),
            server.requests.map { it.path },
        )
        assertTrue(server.requests.all { it.headers["authorization"] == "Bearer k3y" })
        assertEquals(mapOf("group_name" to "brulenet", "draft_type" to "draft_type_post"), form("/api/v1/newdraft"))
        val update = form("/api/v1/updatedraft")
        assertEquals("#announcement ${meeting.subject}", update["subject"])
        assertEquals(meeting.html, update["body"])
        assertEquals("true", update["is_special"])
        assertEquals(mapOf("draft_id" to "42"), form("/api/v1/postdraft"))
        val lookup = server.requests.single { it.path == "/api/v1/getmessages" }
        assertEquals("GET", lookup.method)
        assertEquals("brulenet", decode(lookup.query)["group_name"])
    }

    @Test
    fun `topic settings are set on the topic the post started`() {
        groupsIo()
        val target = DeliveryTarget(
            mapOf("group" to "brulenet", "hashtags" to "announcement", "sticky" to "true", "locked" to "true", "replyTo" to "thread_reply_to_moderators"),
        )

        val result = assertIs<DeliveryResult.Delivered>(plugin.deliver(meeting, target))

        assertEquals("posted to brulenet; pinned, locked, replies to the moderators", result.detail)
        assertEquals(
            mapOf("topic_id" to "555", "is_sticky" to "true", "is_closed" to "true", "reply_to" to "thread_reply_to_moderators"),
            form("/api/v1/updatetopic"),
        )
    }

    @Test
    fun `a post that cannot be found, or whose topic settings are refused, is still delivered and says so`() {
        // Only someone else's older message: not ours.
        groupsIo(messages = Response(200, archive(author = 8)))
        val target = DeliveryTarget(mapOf("group" to "brulenet", "sticky" to "true"))

        val missing = assertIs<DeliveryResult.Delivered>(plugin.deliver(meeting, target))
        assertEquals("posted to brulenet; not found in the archive, so the topic settings were not applied", missing.detail)
        assertEquals(2, server.requests.count { it.path == "/api/v1/getmessages" })
        assertFalse(server.requests.any { it.path == "/api/v1/updatetopic" })

        groupsIo(topic = Response(400, """{"object":"error","type":"inadequate_permissions","extra":"edit_archives"}"""))
        val refused = assertIs<DeliveryResult.Delivered>(plugin.deliver(meeting, target))
        assertEquals("posted to brulenet; topic settings refused: inadequate_permissions: edit_archives", refused.detail)
    }

    @Test
    fun `a post held for approval is delivered and says so, without touching the topic`() {
        groupsIo(post = Response(200, """{"object":"pending post"}"""))

        val held = plugin.deliver(meeting, DeliveryTarget(mapOf("group" to "brulenet", "locked" to "true")))

        assertEquals(DeliveryResult.Delivered("held for moderator approval in brulenet; topic settings not applied"), held)
        assertFalse(server.requests.any { it.path == "/api/v1/getmessages" || it.path == "/api/v1/updatetopic" })
    }

    @Test
    fun `a refused post reports groups io's reason, is not retried, and its draft is discarded`() {
        groupsIo(post = Response(400, """{"object":"error","type":"bad_request","extra":"not subscribed"}"""))

        val result = assertIs<DeliveryResult.Failed>(plugin.deliver(meeting, club))

        assertEquals("groups.io refused the post: bad_request: not subscribed", result.reason)
        assertFalse(result.retryable)
        assertEquals("/api/v1/deletedraft", server.requests.last().path)
        assertEquals("42", form("/api/v1/deletedraft")["draft_id"])
    }

    @Test
    fun `a busy groups io before posting is retried, an unclear answer to the post is not`() {
        server.status = 429
        server.body = ""
        assertTrue(assertIs<DeliveryResult.Failed>(plugin.deliver(meeting, club)).retryable)

        server.requests.clear()
        groupsIo(post = Response(502, ""))
        val unclear = assertIs<DeliveryResult.Failed>(plugin.deliver(meeting, club))
        assertFalse(unclear.retryable)
        assertTrue("may or may not have gone out" in unclear.reason)
        // The draft is kept: it may be the post.
        assertEquals("/api/v1/postdraft", server.requests.last().path)
    }

    @Test
    fun `without an API key it is not ready and says what is missing`() {
        val unconfigured = GroupsIoPlugin().apply { start(FakePluginContext()) }

        assertFalse(unconfigured.status().ready)
        assertTrue("notify.delivery.settings.groupsio.api-key" in unconfigured.status().reason)
        assertIs<DeliveryResult.Failed>(unconfigured.deliver(meeting, club))
    }

    @Test
    fun `group names, hashtags and the reply choice are checked, and subgroups are not offered`() {
        val problems = plugin.validate(
            DeliveryTarget(mapOf("group" to "brulenet+events", "hashtags" to "#ok, #not!ok", "replyTo" to "anywhere")),
        )

        assertEquals(listOf("group", "hashtags", "replyTo"), problems.map { it.field })
        assertEquals(emptyList(), plugin.validate(DeliveryTarget(mapOf("group" to "brulenet", "replyTo" to "thread_reply_to_sender"))))
        assertEquals("brulenet #news", plugin.summarize(DeliveryTarget(mapOf("group" to "brulenet", "hashtags" to "news"))))
    }
}
