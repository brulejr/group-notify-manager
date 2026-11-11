/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit.web

import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.console.ConsoleDatafill
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

/** The audit trail: every change and every delivery, for the user's groups, newest first. */
@Controller
@RequestMapping("/ui/history")
class HistoryPageController(
    private val audit: AuditService,
    private val notifications: NotificationService,
    private val groups: GroupService,
    private val datafill: ConsoleDatafill,
) {

    /** What the type filter offers: a whole category or one type. */
    private val categories = linkedMapOf(
        "deliveries" to setOf(AuditEventType.SENT, AuditEventType.FAILED, AuditEventType.SKIPPED),
        "failures" to setOf(AuditEventType.FAILED, AuditEventType.SKIPPED),
        "changes" to setOf(AuditEventType.CREATED, AuditEventType.UPDATED, AuditEventType.PAUSED, AuditEventType.RESUMED, AuditEventType.DELETED, AuditEventType.COMPLETED),
    )

    @GetMapping
    fun history(
        user: NotifyUser,
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) notificationId: UUID?,
        model: Model,
    ): String {
        rows(user, group, type, notificationId, null, model)
        model.addAttribute("groups", groups.viewable(user, notifications.groupsInUse()).toMutableList())
        model.addAttribute("categories", categories.keys.toMutableList())
        model.addAttribute("types", AuditEventType.entries.toMutableList())
        model.addAttribute("filteredNotification", notificationId?.let { notifications.find(it, user) })
        return "ui/history/list"
    }

    /** One page of rows; with [before], the page after the event with that id. */
    @GetMapping("/rows")
    fun rows(
        user: NotifyUser,
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) notificationId: UUID?,
        @RequestParam(required = false) before: Long?,
        model: Model,
    ): String {
        val types = type?.takeIf { it.isNotBlank() }?.let { categories[it] ?: runCatching { setOf(AuditEventType.valueOf(it)) }.getOrNull() }
        val events = audit.history(
            user,
            AuditQuery(
                groups = group?.takeIf { it.isNotBlank() }?.let { setOf(it) },
                notificationId = notificationId,
                types = types,
                beforeId = before,
                limit = datafill.pageSize,
            ),
        )
        model.addAttribute("events", events.toMutableList())
        model.addAttribute("more", events.size == datafill.pageSize)
        model.addAttribute("lastId", events.lastOrNull()?.id)
        model.addAttribute("group", group.orEmpty())
        model.addAttribute("type", type.orEmpty())
        model.addAttribute("notificationId", notificationId)
        model.addAttribute("firstPage", before == null)
        return "ui/history/list :: rows"
    }
}
