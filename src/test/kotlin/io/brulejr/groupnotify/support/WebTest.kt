/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.support

import io.brulejr.groupnotify.instance.InstanceDraft
import io.brulejr.groupnotify.instance.InstancePort
import io.brulejr.groupnotify.instance.InstanceResult
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.PluginInstance
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

    @Autowired
    lateinit var instanceStore: InstancePort

    @Autowired
    lateinit var instances: InstanceService

    lateinit var mvc: MockMvc

    @BeforeEach
    fun setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        // The context, and its in-memory stores, outlive each test.
        store.list(null).forEach { store.delete(it.id) }
        instanceStore.list(null).forEach { instanceStore.delete(it.id) }
    }

    /**
     * An instance provisioned by an admin: the log plugin's by default, which sends
     * nothing anywhere, as no test may reach a real service.
     */
    fun provision(
        name: String = "Club log",
        plugin: String = "log",
        config: Map<String, String> = mapOf("body" to "subject"),
        group: String = "notify_club",
    ): PluginInstance = (instances.create(InstanceDraft(group, plugin, name, config), TestUsers.admin) as InstanceResult.Saved).instance

    /** Signs the request in as [user], in [groups], as Authelia's forward-auth would. */
    fun MockHttpServletRequestBuilder.signedIn(user: String, vararg groups: String): MockHttpServletRequestBuilder =
        header("Remote-User", user).header("Remote-Groups", groups.joinToString(",")).header("Remote-Name", user.replaceFirstChar(Char::uppercase))

    fun MockHttpServletRequestBuilder.alice() = signedIn("alice", "notify_club", "homelab_users")

    fun MockHttpServletRequestBuilder.bob() = signedIn("bob", "notify_family")

    fun MockHttpServletRequestBuilder.root() = signedIn("root", "homelab_admins")
}
