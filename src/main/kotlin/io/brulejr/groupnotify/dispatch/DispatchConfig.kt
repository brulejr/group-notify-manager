/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.notification.NotificationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Sending notifications: the scheduler that finds them due, and Send now. */
@Configuration
class DispatchConfig {

    @Bean
    fun dispatchService(
        notifications: NotificationService,
        deliveries: DeliveryService,
        instances: InstanceService,
        content: ContentService,
        audit: AuditService,
        groups: GroupService,
        datafill: DispatchDatafill,
        clock: Clock,
    ) = DispatchService(notifications, deliveries, instances, content, audit, groups, datafill, clock)

    /** The schedule itself. Off, nothing is sent unless someone presses Send now. */
    @Bean
    @ConditionalOnProperty("notify.dispatch.enabled", matchIfMissing = true)
    fun dispatchPoller(service: DispatchService, datafill: DispatchDatafill) = DispatchPoller(service, datafill)

    @Bean
    fun dispatchHealthIndicator(service: DispatchService, datafill: DispatchDatafill, clock: Clock) =
        DispatchHealthIndicator(service, datafill, clock)

    @Bean
    fun dispatchInfoContributor(datafill: DispatchDatafill) = DispatchInfoContributor(datafill)
}
