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
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.security.NotifyUser

/**
 * The groups a user may see, each with its notifications counted: what the groups
 * page and API list. Separate from [GroupService], which the notification service
 * itself depends on.
 */
class GroupDirectory(private val groups: GroupService, private val notifications: NotificationService) {

    fun summaries(user: NotifyUser): List<GroupSummary> {
        val byGroup = notifications.list(user).groupBy { it.group }
        return groups.viewable(user, notifications.groupsInUse()).map { name ->
            val own = byGroup[name].orEmpty()
            GroupSummary(
                name = name,
                displayName = groups.displayName(name),
                member = user.memberOf(name),
                canEdit = groups.canEdit(user, name) && groups.isNotificationGroup(name),
                active = own.count { it.status == NotificationStatus.ACTIVE },
                paused = own.count { it.status == NotificationStatus.PAUSED },
                total = own.size,
                nextRunAt = own.mapNotNull { it.nextRunAt }.minOrNull(),
            )
        }
    }
}
