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

    private fun body(group: String = "notify_club", token: String? = "s3cret", deliveryId: String? = null) = """
        {
          "group": "$group",
          "title": "Field day",
          "subject": "Field day is {{date}}",
          "body": "Bring a **radio**.",
          "schedule": {"kind": "RECURRING", "cron": "0 9 * * SAT", "zone": "America/New_York"},
          "deliveries": [{"id": ${deliveryId?.let { "\"$it\"" }}, "plugin": "discord",
                          "target": {"webhookUrl": ${token?.let { "\"https://discord.com/api/webhooks/42/$it\"" } ?: "\"********\""}}}]
        }
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
    fun `secrets are never returned, and sending the mask back keeps them`() {
        val created = create()
        val id = created["id"]
        val deliveryId = ((created["deliveries"] as List<*>).single() as Map<*, *>)["id"].toString()
        mvc.perform(get("/api/notifications/$id").alice())
            .andExpect(jsonPath("$.deliveries[0].target.webhookUrl").value("********"))
            .andExpect(jsonPath("$.deliveries[0].summary").value("webhook 42"))

        mvc.perform(put("/api/notifications/$id").alice().contentType(MediaType.APPLICATION_JSON).content(body(token = null, deliveryId = deliveryId)))
            .andExpect(status().isOk)
        val stored = store.list(null).single().deliveries.single().target["webhookUrl"]
        kotlin.test.assertEquals("https://discord.com/api/webhooks/42/s3cret", stored)
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
        // No SMTP server and no Twilio account in the tests.
        mvc.perform(get("/api/plugins/email").alice()).andExpect(jsonPath("$.ready").value(false))
        mvc.perform(get("/api/plugins/sms").alice()).andExpect(jsonPath("$.ready").value(false))
    }

    @Test
    fun `the scheduler's state is for admins`() {
        mvc.perform(get("/api/dispatch").alice()).andExpect(status().isForbidden)
        mvc.perform(get("/api/dispatch").root()).andExpect(status().isOk)
    }
}
