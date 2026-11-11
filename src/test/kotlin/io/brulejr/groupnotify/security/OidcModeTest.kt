/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * In OIDC mode the application signs people in through Authelia itself, and ignores
 * forward-auth headers. The provider's endpoints are given outright, so nothing is
 * discovered over the network.
 */
@SpringBootTest(
    properties = [
        "notify.security.mode=oidc",
        "spring.security.oauth2.client.registration.authelia.client-id=group-notify",
        "spring.security.oauth2.client.registration.authelia.client-secret=secret",
        "spring.security.oauth2.client.registration.authelia.scope=openid,profile,email,groups",
        "spring.security.oauth2.client.registration.authelia.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.authelia.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.provider.authelia.authorization-uri=https://auth.example.org/api/oidc/authorization",
        "spring.security.oauth2.client.provider.authelia.token-uri=https://auth.example.org/api/oidc/token",
        "spring.security.oauth2.client.provider.authelia.jwk-set-uri=https://auth.example.org/jwks.json",
        "spring.security.oauth2.client.provider.authelia.user-info-uri=https://auth.example.org/api/oidc/userinfo",
        "spring.security.oauth2.client.provider.authelia.user-name-attribute=sub",
    ],
)
@ActiveProfiles("test")
class OidcModeTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Test
    fun `an unsigned-in visitor is sent to Authelia, whatever headers they send`() {
        val mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()

        mvc.perform(get("/ui").header("Remote-User", "mallory").header("Remote-Groups", "homelab_admins"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string("Location", endsWith("/oauth2/authorization/authelia")))
    }
}
