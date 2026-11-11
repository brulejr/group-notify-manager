/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.notification.postgres.PostgresNotificationStore
import io.brulejr.groupnotify.platform.DatabaseMigrator
import io.brulejr.groupnotify.platform.StoreType
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

/** Notifications and their deliveries: where they are kept and who may change them. */
@Configuration
class NotificationConfig {

    /** PostgreSQL is reached only when chosen, so a memory-only context never touches it. */
    @Bean
    fun notificationStore(
        datafill: NotificationDatafill,
        migrator: ObjectProvider<DatabaseMigrator>,
        jdbc: ObjectProvider<JdbcClient>,
        transactionManager: ObjectProvider<PlatformTransactionManager>,
        jsonMapper: JsonMapper,
    ): NotificationPort = when (datafill.store) {
        StoreType.POSTGRES -> {
            migrator.getObject().migrate()
            PostgresNotificationStore(jdbc.getObject(), TransactionTemplate(transactionManager.getObject()), jsonMapper)
        }
        StoreType.MEMORY -> InMemoryNotificationStore()
    }

    @Bean
    fun notificationService(
        store: NotificationPort,
        groups: GroupService,
        deliveries: DeliveryService,
        audit: AuditService,
        datafill: NotificationDatafill,
        clock: Clock,
    ) = NotificationService(store, groups, deliveries, audit, datafill, clock)

    @Bean
    fun notificationHealthIndicator(store: NotificationPort, clock: Clock) = NotificationHealthIndicator(store, clock)

    @Bean
    fun notificationInfoContributor(datafill: NotificationDatafill) = NotificationInfoContributor(datafill)
}
