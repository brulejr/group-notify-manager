/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console.web

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditPort
import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.NotificationResult
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.security.NotifyUser
import io.brulejr.groupnotify.security.SecurityConfig
import io.brulejr.groupnotify.support.WebTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders every console page through the real application context, so a template that
 * names a property wrongly or a fragment that does not exist fails here rather than in
 * a browser.
 */
class ConsolePagesTest : WebTest() {

    @Autowired
    lateinit var notifications: NotificationService

    @Autowired
    lateinit var audit: AuditPort

    private val alice = NotifyUser("alice", "Alice", null, setOf("notify_club"), admin = false, editor = true)

    // Provisioned on first use in each test; the stores are emptied between tests.
    private var discord: PluginInstance? = null
    private var log: PluginInstance? = null

    private fun seed(kind: ScheduleKind = ScheduleKind.RECURRING, format: BodyFormat = BodyFormat.MARKDOWN): UUID {
        val draft = NotificationDraft(
            pattern = "every other month on the 3rd tuesday at 6pm",
            group = "notify_club", title = "Net night", subject = "Net tonight at {{time}}",
            body = if (format == BodyFormat.HTML) "<p>Check in on <strong>146.52</strong></p>" else "Check in on **146.52**",
            format = format, kind = kind, zone = "America/New_York",
            runAt = LocalDateTime.now().plusDays(3), cron = "0 20 * * WED",
            deliveries = listOf(
                NotificationDraft.Delivery(instance = (discord ?: provision("Club Discord", "discord", mapOf("webhookUrl" to "https://discord.com/api/webhooks/7/tok"))).also { discord = it }.id.toString()),
                NotificationDraft.Delivery(instance = (log ?: provision()).also { log = it }.id.toString(), overrides = mapOf("body" to "text")),
            ),
        )
        return (notifications.create(draft, alice) as NotificationResult.Saved).notification.id
    }

    @Test
    fun `the root goes to the dashboard`() {
        mvc.perform(get("/").alice()).andExpect(status().is3xxRedirection).andExpect(header().string("Location", "/ui"))
    }

    @Test
    fun `every page renders`() {
        val recurring = seed()
        val once = seed(ScheduleKind.ONCE, BodyFormat.HTML)
        val pattern = seed(ScheduleKind.PATTERN)
        val pages = mapOf(
            "/ui" to "Coming up",
            "/ui/overview" to "Net night",
            "/ui/notifications" to "Net night",
            "/ui/notifications/rows?status=ACTIVE" to "Net night",
            "/ui/notifications/new" to "New notification",
            "/ui/notifications/$recurring" to "<strong>146.52</strong>",
            "/ui/notifications/$once" to "One-off",
            "/ui/notifications/$recurring/body" to "Next runs",
            "/ui/notifications/$recurring/edit" to "Save changes",
            "/ui/notifications/$once/edit" to "data-format=\"HTML\"",
            "/ui/notifications/$recurring/copy" to "Net night (copy)",
            "/ui/notifications/delivery?instance=${log!!.id}&index=3" to "deliveries[3].overrides.body",
            "/ui/notifications/instances?group=notify_club" to "Club Discord",
            "/ui/instances/list" to "Club log",
            "/ui/notifications/schedule-preview?kind=RECURRING&cron=0 9 * * MON&zone=America/New_York" to "Next runs",
            "/ui/notifications/schedule-preview?kind=RECURRING&cron=nonsense" to "not a valid cron",
            "/ui/notifications/schedule-preview?kind=PATTERN&pattern=every other month on the 3rd tuesday at 6pm&zone=America/New_York" to "Every other month on the 3rd Tuesday at 6:00 PM",
            "/ui/notifications/schedule-preview?kind=PATTERN&pattern=every fortnight" to "“every fortnight” is not understood",
            "/ui/notifications/$pattern" to "Every other month on the 3rd Tuesday",
            "/ui/notifications/$pattern/edit" to "every 2 months on the 3rd tuesday at 18:00 starting",
            "/ui/history" to "Created",
            "/ui/history/rows?type=changes" to "Net night",
            "/ui/history?notificationId=$recurring" to "Showing",
            "/ui/groups" to "Radio Club",
            "/ui/plugins" to "groups.io",
        )
        pages.forEach { (path, text) ->
            mvc.perform(get(path).alice())
                .andExpect(status().isOk)
                .andExpect(content().string(containsString(text)))
        }
    }

    @Test
    fun `a saved secret is never sent to the browser`() {
        val id = seed()

        mvc.perform(get("/ui/notifications/$id/edit").alice()).andExpect(content().string(not(containsString("webhooks/7/tok"))))
        mvc.perform(get("/ui/instances/list").alice()).andExpect(content().string(not(containsString("webhooks/7/tok"))))
        mvc.perform(get("/ui/instances/${discord!!.id}/edit").root())
            .andExpect(content().string(not(containsString("webhooks/7/tok"))))
            .andExpect(content().string(containsString("Saved — leave blank to keep it")))
    }

    @Test
    fun `an admin provisions instances, a member only chooses among them`() {
        val pages = mapOf(
            "/ui/instances/new" to "New delivery instance",
            "/ui/instances/new?plugin=groupsio&group=notify_club" to "config.apiKey",
        )
        pages.forEach { (path, text) -> mvc.perform(get(path).root()).andExpect(status().isOk).andExpect(content().string(containsString(text))) }
        mvc.perform(get("/ui/instances/new?plugin=log").alice()).andExpect(status().isForbidden)

        mvc.perform(
            post("/ui/instances").root().with(csrf())
                .param("group", "notify_club").param("plugin", "log").param("name", "Club log").param("config.body", "text"),
        ).andExpect(header().string("HX-Redirect", startsWith("/ui/plugins?saved=")))
        val created = instanceStore.list(null).single()
        assertEquals(mapOf("body" to "text"), created.config)

        mvc.perform(post("/ui/instances").alice().with(csrf()).param("group", "notify_club").param("plugin", "log").param("name", "Mine"))
            .andExpect(status().isForbidden)
        mvc.perform(get("/ui/instances/${created.id}/edit").root()).andExpect(status().isOk).andExpect(content().string(containsString("Save changes")))

        // The group is a choice when creating, fixed when editing, and still sent with the form.
        mvc.perform(get("/ui/instances/new?plugin=log").root())
            .andExpect(content().string(containsString("<select class=\"form-select\" id=\"group\" name=\"group\"")))
            .andExpect(content().string(containsString("<option value=\"notify_club\">Radio Club</option>")))
        mvc.perform(get("/ui/instances/${created.id}/edit").root())
            .andExpect(content().string(containsString("<input type=\"hidden\" name=\"group\" value=\"notify_club\">")))
            .andExpect(content().string(containsString("disabled=\"disabled\"")))
        mvc.perform(
            post("/ui/instances/${created.id}").root().with(csrf())
                .param("group", "notify_club").param("plugin", "log").param("name", "Club log, renamed").param("config.body", "text"),
        ).andExpect(header().string("HX-Redirect", startsWith("/ui/plugins?saved=")))
        assertEquals("Club log, renamed", instanceStore.find(created.id)!!.name)
    }

    @Test
    fun `an instance in use is not deleted`() {
        seed()

        mvc.perform(delete("/ui/instances/${log!!.id}").root().with(csrf()))
            .andExpect(header().string("HX-Trigger", "instancesChanged"))
            .andExpect(content().string(containsString("Still used by 1 notification: Net night")))
        assertTrue(instanceStore.find(log!!.id) != null)
    }

    @Test
    fun `another group's notification is not found, an admin's view shows everything`() {
        val id = seed()

        mvc.perform(get("/ui/notifications/$id").bob()).andExpect(status().isNotFound)
        mvc.perform(get("/ui/notifications/$id").root()).andExpect(status().isOk)
            .andExpect(content().string(containsString("Health")))
    }

    @Test
    fun `saving the form creates the notification and sends the browser to it`() {
        mvc.perform(
            post("/ui/notifications").alice().with(csrf())
                .param("group", "notify_club").param("title", "Swap meet").param("subject", "Swap meet Saturday")
                .param("format", "MARKDOWN").param("body", "Tables at 8")
                .param("kind", "ONCE").param("runAt", LocalDateTime.now().plusDays(2).withNano(0).toString().take(16)).param("zone", "America/New_York")
                .param("deliveries[5].instance", provision().id.toString()).param("deliveries[5].overrides.body", "subject")
                .param("deliveries[5].enabled", "false").param("deliveries[5].enabled", "true"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("HX-Redirect", startsWith("/ui/notifications/")))

        val saved = store.list(null).single()
        assertEquals("Swap meet", saved.title)
        assertEquals(true, saved.deliveries.single().enabled)
    }

    @Test
    fun `a signed-in request keeps the CSRF cookie it came with`() {
        // Rotating it on every request left each page's token stale as soon as its own
        // fragments loaded.
        val cookie = jakarta.servlet.http.Cookie(SecurityConfig.CSRF_COOKIE, "c0ffee")
        val response = mvc.perform(get("/ui/notifications/new").alice().cookie(cookie)).andExpect(status().isOk).andReturn().response
        assertEquals(emptyList(), response.cookies.filter { it.name == SecurityConfig.CSRF_COOKIE }.map { it.value })
    }

    @Test
    fun `a delivery's web reference links to it in the history, any other is shown as text`() {
        fun sent(reference: String) = AuditEvent(
            occurredAt = java.time.Instant.now(), type = AuditEventType.SENT, actor = AuditEvent.SCHEDULER,
            notificationId = UUID.randomUUID(), notificationTitle = "Net night", group = "notify_club",
            plugin = "groupsio", target = "brulenet", detail = "posted to brulenet", reference = reference,
        )
        audit.record(sent("https://groups.io/g/brulenet/message/321"))
        audit.record(sent("javascript:alert(1)"))

        mvc.perform(get("/ui/history").alice()).andExpect(status().isOk)
            .andExpect(content().string(containsString("href=\"https://groups.io/g/brulenet/message/321\"")))
            .andExpect(content().string(not(containsString("href=\"javascript:"))))
    }

    @Test
    fun `a stale CSRF token is reported as such, so the console can ask for a reload`() {
        mvc.perform(post("/ui/notifications").alice().header("X-XSRF-TOKEN", "stale").param("title", "Swap meet"))
            .andExpect(status().isForbidden)
            .andExpect(header().string(SecurityConfig.DENIED_HEADER, "csrf"))
    }

    @Test
    fun `a refused form is redrawn with each problem against its input`() {
        mvc.perform(
            post("/ui/notifications").alice().with(csrf())
                .param("group", "notify_club").param("title", "").param("kind", "RECURRING").param("cron", "0 25 * * *")
                .param("deliveries[0].instance", provision().id.toString()).param("deliveries[0].overrides.body", "everything"),
        )
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist("HX-Redirect"))
            .andExpect(content().string(containsString("is-invalid")))
            .andExpect(content().string(containsString("must be one of the choices")))
            .andExpect(content().string(containsString("not a valid cron expression")))
        assertEquals(0, store.list(null).size)
    }

    @Test
    fun `a form without its CSRF token is refused`() {
        mvc.perform(post("/ui/notifications").alice().param("title", "x")).andExpect(status().isForbidden)
    }

    @Test
    fun `pausing from the console says so and tells the lists to reload`() {
        val id = seed()

        mvc.perform(post("/ui/notifications/$id/pause").alice().with(csrf()))
            .andExpect(header().string("HX-Trigger", "notificationsChanged"))
            .andExpect(content().string(containsString("Paused Net night")))
    }

    @Test
    fun `send now reports each delivery`() {
        // The log plugin only: nothing in the tests may reach a real service.
        val draft = NotificationDraft(
            group = "notify_club", title = "Log only", subject = "s", body = "b", format = BodyFormat.MARKDOWN,
            kind = ScheduleKind.RECURRING, cron = "0 20 * * WED",
            deliveries = listOf(
                NotificationDraft.Delivery(instance = provision().id.toString()),
                NotificationDraft.Delivery(instance = provision("Club log, in full", config = mapOf("body" to "text")).id.toString()),
            ),
        )
        val id = (notifications.create(draft, alice) as NotificationResult.Saved).notification.id

        mvc.perform(post("/ui/notifications/$id/send").alice().with(csrf()))
            .andExpect(header().string("HX-Trigger", "notificationsChanged"))
            .andExpect(content().string(containsString("Sent through 2 deliveries")))
        mvc.perform(get("/ui/notifications/$id").alice()).andExpect(content().string(containsString("bi-hand-index")))
    }

    @Test
    fun `the editor's preview and conversion work on unsaved text`() {
        mvc.perform(post("/ui/content/preview").alice().with(csrf()).param("body", "**hi**").param("format", "MARKDOWN"))
            .andExpect(content().string(containsString("<strong>hi</strong>")))
        mvc.perform(post("/ui/content/convert").alice().with(csrf()).param("body", "<p><em>hi</em></p>").param("from", "HTML").param("to", "MARKDOWN"))
            .andExpect(content().string("*hi*"))
    }
}
