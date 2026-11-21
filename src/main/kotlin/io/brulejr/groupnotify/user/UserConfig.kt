/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user

import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.platform.DatabaseMigrator
import io.brulejr.groupnotify.platform.StoreType
import io.brulejr.groupnotify.user.postgres.PostgresUserStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

/** Who uses the system: their records, kept as they make requests, for the admins' Users page. */
@Configuration
class UserConfig {

    /** PostgreSQL is reached only when chosen, so a memory-only context never touches it. */
    @Bean
    fun userStore(
        datafill: UserDatafill,
        migrator: ObjectProvider<DatabaseMigrator>,
        jdbc: ObjectProvider<JdbcClient>,
        jsonMapper: JsonMapper,
    ): UserPort = when (datafill.store) {
        StoreType.POSTGRES -> {
            migrator.getObject().migrate()
            PostgresUserStore(jdbc.getObject(), jsonMapper)
        }
        StoreType.MEMORY -> InMemoryUserStore()
    }

    @Bean
    fun userService(store: UserPort, audit: AuditService, datafill: UserDatafill, clock: Clock) = UserService(store, audit, datafill, clock)

    /** Records every signed-in request to the console and the API; not the assets, not health. */
    @Bean
    fun userActivityWebMvcConfigurer(users: UserService): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(UserActivityInterceptor(users)).addPathPatterns("/ui/**", "/api/**")
        }
    }

    @Bean
    fun userHealthIndicator(users: UserService) = UserHealthIndicator(users)

    @Bean
    fun userInfoContributor(datafill: UserDatafill) = UserInfoContributor(datafill)
}
