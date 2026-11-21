/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user.web

import io.brulejr.groupnotify.support.WebTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The admins' Users page: who is recorded as they use the system, and who may see it. */
class UserPagesTest : WebTest() {

    @Test
    fun `a signed-in request records the person, with the address the proxy reports`() {
        mvc.perform(get("/ui").alice().header("X-Forwarded-For", "203.0.113.7, 172.18.0.2").header("User-Agent", "Mozilla/5.0 (X11; Linux) Firefox/140.0"))
            .andExpect(status().isOk)

        val r = userStore.find("alice")!!
        assertEquals("203.0.113.7", r.lastAddress)
        assertEquals(setOf("notify_club", "homelab_users"), r.groups)
        // Health checks and assets are not anyone's activity.
        mvc.perform(get("/mgmt/health")).andExpect(status().isOk)
        assertEquals(1, userStore.list().size)
    }

    @Test
    fun `admins see the Users page and each person's, with LLDAP managing the accounts`() {
        mvc.perform(get("/ui").alice()).andExpect(status().isOk)

        mvc.perform(get("/ui/users").root()).andExpect(status().isOk)
            .andExpect(content().string(containsString("/ui/users/alice")))
            .andExpect(content().string(containsString("Radio Club")))
        mvc.perform(get("/ui/users/alice").root()).andExpect(status().isOk)
            .andExpect(content().string(containsString("What they did")))
            .andExpect(content().string(containsString("homelab_users")))
        mvc.perform(get("/ui/users/nobody").root()).andExpect(status().isNotFound)
        mvc.perform(get("/ui").root()).andExpect(content().string(containsString("href=\"/ui/users\"")))
    }

    @Test
    fun `nobody but an admin sees who uses the system`() {
        mvc.perform(get("/ui").alice()).andExpect(content().string(not(containsString("href=\"/ui/users\""))))
        mvc.perform(get("/ui/users").alice()).andExpect(status().isForbidden)
        mvc.perform(get("/ui/users/alice").alice()).andExpect(status().isForbidden)
        mvc.perform(get("/api/users").alice()).andExpect(status().isForbidden)
        mvc.perform(delete("/api/users/alice").alice().header("X-Requested-With", "test")).andExpect(status().isForbidden)
    }

    @Test
    fun `the API lists people for an admin, and forgetting one deletes the record only`() {
        mvc.perform(get("/ui").alice()).andExpect(status().isOk)

        mvc.perform(get("/api/users").root())
            .andExpect(jsonPath("$[?(@.username == 'alice')].visits").value(1))
            .andExpect(jsonPath("$[?(@.username == 'root')].admin").value(true))
        mvc.perform(delete("/ui/users/alice").root().with(csrf())).andExpect(header().string("HX-Redirect", "/ui/users"))
        assertNull(userStore.find("alice"))
        mvc.perform(delete("/api/users/alice").root().header("X-Requested-With", "test")).andExpect(status().isNotFound)
    }
}
