/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert.web

import io.brulejr.groupnotify.support.WebTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A group's members mark an instance for its alerts, and test it. */
class AlertPagesTest : WebTest() {

    private fun save(alerts: List<String>, name: String = "Organisers") = mvc.perform(
        post("/ui/instances").alice().with(csrf())
            .param("group", "notify_club").param("plugin", "log").param("name", name).param("config.body", "text")
            .param("alerts", *alerts.toTypedArray()),
    ).andExpect(header().string("HX-Redirect", startsWith("/ui/plugins?saved=")))

    @Test
    fun `the instance form's switch marks it for alerts, and the list offers a test`() {
        mvc.perform(get("/ui/instances/new?plugin=log").alice())
            .andExpect(content().string(containsString("Send this group's alerts here")))

        save(listOf("false", "true"))
        save(listOf("false"), name = "Plain")
        val marked = instanceStore.list(null).single { it.name == "Organisers" }
        assertTrue(marked.alerts)
        assertFalse(instanceStore.list(null).single { it.name == "Plain" }.alerts)

        mvc.perform(get("/ui/instances/list").alice())
            .andExpect(content().string(containsString("/ui/alerts/test/${marked.id}")))
            .andExpect(content().string(containsString("bi-bell me-1\"></i>Alerts")))
        mvc.perform(get("/ui/instances/${marked.id}/edit").alice())
            .andExpect(content().string(containsString("name=\"alerts\" value=\"true\" checked")))

        mvc.perform(post("/ui/alerts/test/${marked.id}").alice().with(csrf()))
            .andExpect(content().string(containsString("Test alert sent through Organisers")))
        mvc.perform(get("/ui/history").alice()).andExpect(content().string(containsString("test alert sent")))
    }

    @Test
    fun `only the group's members may test its alerts`() {
        save(listOf("false", "true"))
        val marked = instanceStore.list(null).single()

        mvc.perform(post("/ui/alerts/test/${marked.id}").bob().with(csrf()))
            .andExpect(content().string(containsString("No such delivery instance")))
            .andExpect(content().string(not(containsString("Test alert sent"))))
        mvc.perform(post("/api/alerts/test/${marked.id}").bob().header("X-Requested-With", "test")).andExpect(status().isNotFound)
        mvc.perform(post("/api/alerts/test/${marked.id}").root().header("X-Requested-With", "test"))
            .andExpect(jsonPath("$.sent").value(true))
            .andExpect(jsonPath("$.instance").value("Organisers"))
        assertEquals(1, instanceStore.list(null).size)
    }
}
