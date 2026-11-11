/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console

import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.notification.NotificationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * The console under `/ui`: server-rendered pages over every feature's service. Its page
 * controllers live in each feature's `web` package; this declares what they share.
 */
@Configuration
class ConsoleConfig {

    @Bean
    fun consoleService(
        notifications: NotificationService,
        audit: AuditService,
        deliveries: DeliveryService,
        groups: GroupService,
        clock: Clock,
    ) = ConsoleService(notifications, audit, deliveries, groups, clock)

    @Bean
    fun consoleHealthIndicator() = ConsoleHealthIndicator()

    @Bean
    fun consoleInfoContributor(datafill: ConsoleDatafill) = ConsoleInfoContributor(datafill)
}
