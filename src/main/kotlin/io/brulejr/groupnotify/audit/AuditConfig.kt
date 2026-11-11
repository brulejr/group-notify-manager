/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

import io.brulejr.groupnotify.audit.postgres.PostgresAuditStore
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.platform.DatabaseMigrator
import io.brulejr.groupnotify.platform.StoreType
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock

/** The audit trail: every change to a notification and every delivery made of it. */
@Configuration
class AuditConfig {

    /** PostgreSQL is reached only when chosen, so a memory-only context never touches it. */
    @Bean
    fun auditStore(datafill: AuditDatafill, migrator: ObjectProvider<DatabaseMigrator>, jdbc: ObjectProvider<JdbcClient>): AuditPort =
        when (datafill.store) {
            StoreType.POSTGRES -> {
                migrator.getObject().migrate()
                PostgresAuditStore(jdbc.getObject())
            }
            StoreType.MEMORY -> InMemoryAuditStore()
        }

    @Bean
    fun auditService(store: AuditPort, groups: GroupService, datafill: AuditDatafill, clock: Clock) =
        AuditService(store, groups, datafill, clock)

    @Bean
    fun auditHealthIndicator(service: AuditService, clock: Clock) = AuditHealthIndicator(service, clock)

    @Bean
    fun auditInfoContributor(datafill: AuditDatafill) = AuditInfoContributor(datafill)
}
