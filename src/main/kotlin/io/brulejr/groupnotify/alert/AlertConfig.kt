/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert

import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Failure alerts, posted to each group's instances marked for them; an [io.brulejr.groupnotify.audit.AuditListener]. */
@Configuration
class AlertConfig {

    @Bean
    fun alertService(
        audit: AuditService,
        instances: InstanceService,
        deliveries: DeliveryService,
        content: ContentService,
        groups: GroupService,
        datafill: AlertDatafill,
        clock: Clock,
    ) = AlertService(audit, instances, deliveries, content, groups, datafill, clock)

    @Bean
    fun alertHealthIndicator(alerts: AlertService, datafill: AlertDatafill, clock: Clock) = AlertHealthIndicator(alerts, datafill, clock)

    @Bean
    fun alertInfoContributor(datafill: AlertDatafill) = AlertInfoContributor(datafill)
}
