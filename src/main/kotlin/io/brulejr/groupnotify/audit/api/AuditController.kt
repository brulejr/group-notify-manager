/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit.api

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * The audit trail, newest first, limited to the caller's groups. Page with `before`, the
 * id of the last event of the previous page.
 */
@RestController
@RequestMapping("/api/audit")
class AuditController(private val audit: AuditService) {

    @GetMapping
    fun history(
        user: NotifyUser,
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) notificationId: UUID?,
        @RequestParam(required = false) type: List<AuditEventType>?,
        @RequestParam(required = false) since: Instant?,
        @RequestParam(required = false) before: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<AuditEvent> = audit.history(
        user,
        AuditQuery(
            groups = group?.let { setOf(it) },
            notificationId = notificationId,
            types = type?.toSet(),
            since = since,
            beforeId = before,
            limit = limit.coerceIn(1, 1000),
        ),
    )
}
