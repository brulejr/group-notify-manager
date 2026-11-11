/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.support

import io.brulejr.groupnotify.notification.NotificationPort
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * The whole application, with the security filters, every store in memory and the
 * scheduler off (see application-test.yml). Requests sign in the way Traefik and
 * Authelia sign them in: `Remote-*` headers.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class WebTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var store: NotificationPort

    lateinit var mvc: MockMvc

    @BeforeEach
    fun setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        // The context, and its in-memory store, outlive each test.
        store.list(null).forEach { store.delete(it.id) }
    }

    /** Signs the request in as [user], in [groups], as Authelia's forward-auth would. */
    fun MockHttpServletRequestBuilder.signedIn(user: String, vararg groups: String): MockHttpServletRequestBuilder =
        header("Remote-User", user).header("Remote-Groups", groups.joinToString(",")).header("Remote-Name", user.replaceFirstChar(Char::uppercase))

    fun MockHttpServletRequestBuilder.alice() = signedIn("alice", "notify_club", "homelab_users")

    fun MockHttpServletRequestBuilder.bob() = signedIn("bob", "notify_family")

    fun MockHttpServletRequestBuilder.root() = signedIn("root", "homelab_admins")
}
