/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfException
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Who may reach what. Authelia signs people in, by forward-auth headers or OIDC (see
 * [AuthMode]); group visibility is the services' business, not the URL rules'. Every
 * page and API call needs a signed-in user, the management endpoints an admin, and
 * health nobody, so a container check can call it.
 */
@Configuration
class SecurityConfig {

    private val log = LoggerFactory.getLogger(SecurityConfig::class.java)

    @Bean
    fun securityService(datafill: SecurityDatafill) = SecurityService(datafill)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        datafill: SecurityDatafill,
        security: SecurityService,
        clients: ObjectProvider<ClientRegistrationRepository>,
    ): SecurityFilterChain {
        http.authorizeHttpRequests {
            it.requestMatchers("/assets/**", "/webjars/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/mgmt/health", "/mgmt/health/**").permitAll()
                .requestMatchers("/mgmt/**").hasRole("ADMIN")
                .anyRequest().authenticated()
        }
        // A cookie rather than the session, so forward-auth can stay stateless. The
        // console sends the token as a header on every htmx request. The cookie has its own
        // name: cookies are not kept apart by port, so other apps on localhost that use
        // Spring's default XSRF-TOKEN would otherwise replace it under an open page.
        http.csrf {
            it.csrfTokenRepository(CookieCsrfTokenRepository().apply { setCookieName(CSRF_COOKIE) })
                .ignoringRequestMatchers(ApiCsrfExemption)
        }
        // A refused CSRF token says so, so the console can ask for a reload rather than
        // claim the user lacks permission.
        http.exceptionHandling { handling ->
            val fallback = AccessDeniedHandlerImpl()
            handling.accessDeniedHandler { request, response, denied ->
                if (denied is CsrfException) response.setHeader(DENIED_HEADER, "csrf")
                fallback.handle(request, response, denied)
            }
        }
        when (datafill.mode) {
            AuthMode.FORWARD_AUTH -> {
                if (datafill.devUser != null) {
                    log.warn("notify.security.dev-user is set: requests without forward-auth headers are signed in as '{}'", datafill.devUser.username)
                }
                // Stateless, every request arrives signed in, and Spring treats each as a fresh
                // sign-in, on which the CSRF token is rotated: the cookie would change on
                // every request, leaving the page's token stale as soon as a fragment loads.
                // Rotation guards a sign-in, and forward-auth has none here.
                http.csrf { it.sessionAuthenticationStrategy(NullAuthenticatedSessionStrategy()) }
                http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
                    .addFilterBefore(ForwardAuthFilter(datafill, security), AnonymousAuthenticationFilter::class.java)
                    .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
                    .logout { it.disable() }
            }
            AuthMode.OIDC -> {
                val registrations = checkNotNull(clients.ifAvailable) {
                    "notify.security.mode=oidc needs spring.security.oauth2.client.registration.${datafill.oidc.registrationId}.*"
                }
                http.oauth2Login { login ->
                    login.userInfoEndpoint { it.oidcUserService(NotifyOidcUserService(datafill.oidc, security)) }
                }.logout { logout ->
                    logout.logoutSuccessHandler(
                        OidcClientInitiatedLogoutSuccessHandler(registrations).apply { setPostLogoutRedirectUri("{baseUrl}/") },
                    )
                }
            }
        }
        return http.build()
    }

    @Bean
    fun notifyUserWebMvcConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
            resolvers += NotifyUserArgumentResolver()
        }
    }

    @Bean
    fun securityHealthIndicator(datafill: SecurityDatafill) = SecurityHealthIndicator(datafill)

    @Bean
    fun securityInfoContributor(datafill: SecurityDatafill) = SecurityInfoContributor(datafill)

    companion object {
        /** The CSRF cookie; see [securityFilterChain]. */
        const val CSRF_COOKIE = "NOTIFY-XSRF-TOKEN"

        /** Set to `csrf` on a 403 caused by a missing or stale CSRF token. */
        const val DENIED_HEADER = "X-Notify-Denied"
    }
}
