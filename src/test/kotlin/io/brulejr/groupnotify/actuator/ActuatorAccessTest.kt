/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.actuator

import io.brulejr.groupnotify.support.WebTest
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Every feature contributes to health and info; health is open, the rest is for admins. */
class ActuatorAccessTest : WebTest() {

    @Test
    fun `health is open to a container check, with every feature's indicator`() {
        val features = listOf("security", "group", "audit", "delivery", "content", "notification", "dispatch", "console", "database", "instance")
        val result = mvc.perform(get("/mgmt/health")).andExpect(status().isOk)
        features.forEach { result.andExpect(jsonPath("$.components.$it.status").value("UP")) }
    }

    @Test
    fun `info and metrics are for admins`() {
        mvc.perform(get("/mgmt/info")).andExpect(status().isUnauthorized)
        mvc.perform(get("/mgmt/info").alice()).andExpect(status().isForbidden)
        val result = mvc.perform(get("/mgmt/info").root()).andExpect(status().isOk)
        listOf("security", "groups", "audit", "delivery", "content", "notifications", "dispatch", "console").forEach {
            result.andExpect(jsonPath("$.$it", notNullValue()))
        }
    }
}
