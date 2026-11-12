/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.api

import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.notification.NotificationFilter
import io.brulejr.groupnotify.notification.NotificationResult
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.notification.NotificationStatus
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Notifications, limited to the caller's groups. One in a group the caller is not in is
 * not found, rather than forbidden; one they can see but not change is forbidden.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notifications: NotificationService,
    private val instances: InstanceService,
    private val clock: Clock,
) {

    @GetMapping
    fun list(
        user: NotifyUser,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) status: NotificationStatus?,
    ): List<NotificationView> = notifications.list(user, NotificationFilter(q, group, status)).map { NotificationView.of(it, instances) }

    @GetMapping("/{id}")
    fun get(user: NotifyUser, @PathVariable id: UUID): ResponseEntity<NotificationView> =
        notifications.find(id, user)?.let { ResponseEntity.ok(NotificationView.of(it, instances)) } ?: ResponseEntity.notFound().build()

    /** The next runs, as instants. */
    @GetMapping("/{id}/upcoming")
    fun upcoming(user: NotifyUser, @PathVariable id: UUID, @RequestParam(defaultValue = "5") count: Int): ResponseEntity<List<Instant>> {
        val notification = notifications.find(id, user) ?: return ResponseEntity.notFound().build()
        if (notification.status != NotificationStatus.ACTIVE) return ResponseEntity.ok(emptyList())
        return ResponseEntity.ok(notification.schedule.upcoming(clock.instant(), count.coerceIn(1, 50)))
    }

    @PostMapping
    fun create(user: NotifyUser, @RequestBody request: NotificationRequest): ResponseEntity<Any> =
        respond(notifications.create(request.toDraft(), user), HttpStatus.CREATED)

    @PutMapping("/{id}")
    fun update(user: NotifyUser, @PathVariable id: UUID, @RequestBody request: NotificationRequest): ResponseEntity<Any> =
        respond(notifications.update(id, request.toDraft(), user))

    @PostMapping("/{id}/pause")
    fun pause(user: NotifyUser, @PathVariable id: UUID) = respond(notifications.pause(id, user))

    @PostMapping("/{id}/resume")
    fun resume(user: NotifyUser, @PathVariable id: UUID) = respond(notifications.resume(id, user))

    @DeleteMapping("/{id}")
    fun delete(user: NotifyUser, @PathVariable id: UUID): ResponseEntity<Any> = when (val result = notifications.delete(id, user)) {
        is NotificationResult.Saved -> ResponseEntity.noContent().build()
        else -> respond(result)
    }

    private fun respond(result: NotificationResult, success: HttpStatus = HttpStatus.OK): ResponseEntity<Any> = when (result) {
        is NotificationResult.Saved -> ResponseEntity.status(success).body(NotificationView.of(result.notification, instances))
        is NotificationResult.Invalid -> ResponseEntity.badRequest().body(NotificationErrors.of(result.problems))
        NotificationResult.NotFound -> ResponseEntity.notFound().build()
        NotificationResult.Forbidden -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
}
