/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.event.postgres.PostgresEventStore
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.InstanceUsage
import io.brulejr.groupnotify.notification.NotificationDatafill
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

/** Events: where they are kept, who may change them, and which instances they use. */
@Configuration
class EventConfig {

    /** PostgreSQL is reached only when chosen, so a memory-only context never touches it. */
    @Bean
    fun eventStore(
        datafill: EventDatafill,
        migrator: ObjectProvider<DatabaseMigrator>,
        jdbc: ObjectProvider<JdbcClient>,
        transactionManager: ObjectProvider<PlatformTransactionManager>,
        jsonMapper: JsonMapper,
    ): EventPort = when (datafill.store) {
        StoreType.POSTGRES -> {
            migrator.getObject().migrate()
            PostgresEventStore(jdbc.getObject(), TransactionTemplate(transactionManager.getObject()), jsonMapper)
        }
        StoreType.MEMORY -> InMemoryEventStore()
    }

    /** Which events send through an instance, so an instance in use is not deleted. */
    @Bean
    fun eventInstanceUsage(store: EventPort) = InstanceUsage { id ->
        store.list(null).filter { e -> e.allDeliveries.any { it.instance == id } }.map { "event: ${it.title}" }
    }

    /** New events take the notifications' default time zone. */
    @Bean
    fun eventService(
        store: EventPort,
        groups: GroupService,
        instances: InstanceService,
        content: ContentService,
        audit: AuditService,
        datafill: EventDatafill,
        notificationDatafill: NotificationDatafill,
        clock: Clock,
    ) = EventService(store, groups, instances, content, audit, datafill, notificationDatafill.defaultZone, clock)

    @Bean
    fun eventHealthIndicator(store: EventPort, clock: Clock) = EventHealthIndicator(store, clock)

    @Bean
    fun eventInfoContributor(datafill: EventDatafill) = EventInfoContributor(datafill)
}
