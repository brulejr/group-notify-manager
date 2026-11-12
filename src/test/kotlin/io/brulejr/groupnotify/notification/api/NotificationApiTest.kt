/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.api

import io.brulejr.groupnotify.support.WebTest
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import org.springframework.beans.factory.annotation.Autowired

/** The REST API through the real security filters: who may see and change what. */
class NotificationApiTest : WebTest() {

    @Autowired
    lateinit var json: JsonMapper

    /** The club's Discord channel, provisioned by an admin on first use in each test. */
    private val discord by lazy { provision("Club Discord", "discord", mapOf("webhookUrl" to "https://discord.com/api/webhooks/42/s3cret")) }

    private fun body(group: String = "notify_club", overrides: String = "{}") = """
        {
          "group": "$group",
          "title": "Field day",
          "subject": "Field day is {{date}}",
          "body": "Bring a **radio**.",
          "schedule": {"kind": "RECURRING", "cron": "0 9 * * SAT", "zone": "America/New_York"},
          "deliveries": [{"instance": "${discord.id}", "overrides": $overrides}]
        }
    """.trimIndent()

    private fun instanceBody(name: String = "Club Discord", webhook: String = "https://discord.com/api/webhooks/42/s3cret") = """
        {"group": "notify_club", "plugin": "discord", "name": "$name", "config": {"webhookUrl": "$webhook", "mention": "@here"}}
    """.trimIndent()

    private fun create(): Map<*, *> {
        val response = mvc.perform(post("/api/notifications").alice().contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return json.readValue(response, Map::class.java)
    }

    @Test
    fun `without Authelia's headers nothing is answered`() {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized)
        mvc.perform(get("/ui")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `the caller's identity and groups come from the headers`() {
        mvc.perform(get("/api/me").root())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("root"))
            .andExpect(jsonPath("$.admin").value(true))
        mvc.perform(get("/api/me").alice()).andExpect(jsonPath("$.admin").value(false))
    }

    @Test
    fun `a group's notifications are seen by its members and admins only`() {
        val id = create()["id"]

        mvc.perform(get("/api/notifications").alice()).andExpect(jsonPath("$", hasSize<Any>(1)))
        mvc.perform(get("/api/notifications").bob()).andExpect(jsonPath("$", hasSize<Any>(0)))
        mvc.perform(get("/api/notifications/$id").bob()).andExpect(status().isNotFound)
        mvc.perform(get("/api/notifications/$id").root()).andExpect(status().isOk)
        mvc.perform(get("/api/audit").bob()).andExpect(jsonPath("$", hasSize<Any>(0)))
        mvc.perform(get("/api/audit").alice()).andExpect(jsonPath("$[0].type").value("CREATED"))
    }

    @Test
    fun `a notification names its instance and only overrides what the plugin allows`() {
        val created = create()

        mvc.perform(get("/api/notifications/${created["id"]}").alice())
            .andExpect(jsonPath("$.deliveries[0].instanceName").value("Club Discord"))
            .andExpect(jsonPath("$.deliveries[0].summary").value("webhook 42"))
        mvc.perform(
            put("/api/notifications/${created["id"]}").alice().contentType(MediaType.APPLICATION_JSON)
                .content(body(overrides = """{"mention": "@everyone", "webhookUrl": "https://discord.com/api/webhooks/1/evil"}""")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deliveries[0].overrides.mention").value("@everyone"))
            .andExpect(jsonPath("$.deliveries[0].overrides.webhookUrl").doesNotExist())
    }

    @Test
    fun `only an admin provisions an instance, its secrets are never returned, and the mask keeps them`() {
        mvc.perform(post("/api/instances").alice().contentType(MediaType.APPLICATION_JSON).content(instanceBody()))
            .andExpect(status().isForbidden)
        val created = json.readValue(
            mvc.perform(post("/api/instances").root().contentType(MediaType.APPLICATION_JSON).content(instanceBody()))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.config.webhookUrl").value("********"))
                .andExpect(jsonPath("$.overridable", hasSize<Any>(2)))
                .andReturn().response.contentAsString,
            Map::class.java,
        )
        val id = created["id"]
        // Sealed at rest, under the run's key.
        kotlin.test.assertTrue(instanceStore.list(null).single().config.getValue("webhookUrl").startsWith("enc:v1:"))

        mvc.perform(get("/api/instances?group=notify_club").alice()).andExpect(jsonPath("$[0].config.webhookUrl").value("********"))
        mvc.perform(get("/api/instances/$id").bob()).andExpect(status().isNotFound)
        mvc.perform(put("/api/instances/$id").root().contentType(MediaType.APPLICATION_JSON).content(instanceBody(name = "Renamed", webhook = "********")))
            .andExpect(status().isOk)
        val stored = instanceStore.list(null).single()
        kotlin.test.assertEquals("Renamed", stored.name)
        kotlin.test.assertEquals("https://discord.com/api/webhooks/42/s3cret", instances.target(stored, emptyMap())["webhookUrl"])
        mvc.perform(get("/api/audit").root()).andExpect(jsonPath("$[0].detail").value("changed name Club Discord → Renamed"))
    }

    @Test
    fun `an instance in use cannot be deleted`() {
        create()

        mvc.perform(delete("/api/instances/${discord.id}").root().header("X-Requested-With", "test"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.notifications[0]").value("Field day"))
        mvc.perform(delete("/api/instances/${discord.id}").alice().header("X-Requested-With", "test")).andExpect(status().isForbidden)
    }

    @Test
    fun `a refused notification says what is wrong with each field`() {
        mvc.perform(post("/api/notifications").bob().contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.problems[0].field").value("group"))
    }

    @Test
    fun `a bodyless call needs X-Requested-With, which no other site can send`() {
        val id = create()["id"]

        mvc.perform(post("/api/notifications/$id/pause").alice()).andExpect(status().isForbidden)
        mvc.perform(post("/api/notifications/$id/pause").alice().header("X-Requested-With", "test"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAUSED"))
        mvc.perform(post("/api/notifications/$id/resume").bob().header("X-Requested-With", "test")).andExpect(status().isNotFound)
        mvc.perform(delete("/api/notifications/$id").alice().header("X-Requested-With", "test")).andExpect(status().isNoContent)
    }

    @Test
    fun `the plugins are listed with their fields and readiness`() {
        mvc.perform(get("/api/plugins/discord").alice())
            .andExpect(jsonPath("$.ready").value(true))
            .andExpect(jsonPath("$.fields[0].key").value("webhookUrl"))
            .andExpect(jsonPath("$.fields[0].secret").value(true))
            .andExpect(jsonPath("$.fields[0].overridable").value(false))
        // Its key is an instance's now, not the installation's.
        mvc.perform(get("/api/plugins/groupsio").alice()).andExpect(jsonPath("$.ready").value(true))
    }

    @Test
    fun `the scheduler's state is for admins`() {
        mvc.perform(get("/api/dispatch").alice()).andExpect(status().isForbidden)
        mvc.perform(get("/api/dispatch").root()).andExpect(status().isOk)
    }
}
