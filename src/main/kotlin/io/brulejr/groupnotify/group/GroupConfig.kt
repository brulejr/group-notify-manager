/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.group

import io.brulejr.groupnotify.notification.NotificationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Notification groups: which of a user's Authelia groups hold notifications, and who sees them. */
@Configuration
class GroupConfig {

    @Bean
    fun groupService(datafill: GroupDatafill) = GroupService(datafill)

    @Bean
    fun groupDirectory(groups: GroupService, notifications: NotificationService) = GroupDirectory(groups, notifications)

    @Bean
    fun groupHealthIndicator(datafill: GroupDatafill) = GroupHealthIndicator(datafill)

    @Bean
    fun groupInfoContributor(datafill: GroupDatafill) = GroupInfoContributor(datafill)
}
