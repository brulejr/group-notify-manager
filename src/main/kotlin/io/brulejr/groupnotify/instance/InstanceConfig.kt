/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.postgres.PostgresInstanceStore
import io.brulejr.groupnotify.platform.DatabaseMigrator
import io.brulejr.groupnotify.platform.StoreType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

/** Plugin instances: where they are kept, the key their secrets are sealed with, and who may change them. */
@Configuration
class InstanceConfig {

    private val log = LoggerFactory.getLogger(InstanceConfig::class.java)

    /** PostgreSQL is reached only when chosen, so a memory-only context never touches it. */
    @Bean
    fun instanceStore(
        datafill: InstanceDatafill,
        migrator: ObjectProvider<DatabaseMigrator>,
        jdbc: ObjectProvider<JdbcClient>,
        jsonMapper: JsonMapper,
    ): InstancePort = when (datafill.store) {
        StoreType.POSTGRES -> {
            migrator.getObject().migrate()
            PostgresInstanceStore(jdbc.getObject(), jsonMapper)
        }
        StoreType.MEMORY -> InMemoryInstanceStore()
    }

    @Bean
    fun secretCipher(datafill: InstanceDatafill): SecretCipher = SecretCipher(datafill.secretKey).also {
        if (it.ephemeral) {
            log.warn(
                "notify.instances.secret-key (NOTIFY_SECRET_KEY) is not set: instance secrets are sealed with a key " +
                    "for this run only{}",
                if (datafill.store == StoreType.POSTGRES) ", and those stored now cannot be read after a restart" else "",
            )
        }
    }

    @Bean
    fun instanceService(
        store: InstancePort,
        deliveries: DeliveryService,
        groups: GroupService,
        usage: InstanceUsage,
        audit: AuditService,
        cipher: SecretCipher,
        datafill: InstanceDatafill,
        clock: Clock,
    ) = InstanceService(store, deliveries, groups, usage, audit, cipher, datafill, clock)

    @Bean
    fun instanceHealthIndicator(store: InstancePort, cipher: SecretCipher, datafill: InstanceDatafill) =
        InstanceHealthIndicator(store, cipher, datafill)

    @Bean
    fun instanceInfoContributor(datafill: InstanceDatafill, cipher: SecretCipher) = InstanceInfoContributor(datafill, cipher)
}
