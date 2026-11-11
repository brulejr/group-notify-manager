/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch.api

import io.brulejr.groupnotify.dispatch.DispatchService
import io.brulejr.groupnotify.dispatch.DispatchStats
import io.brulejr.groupnotify.dispatch.SendResult
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Sending: Send now for one notification, and the scheduler's own state for admins. */
@RestController
class DispatchController(private val dispatch: DispatchService) {

    data class DeliveryReportView(val deliveryId: UUID, val plugin: String, val summary: String, val delivered: Boolean, val detail: String, val attempts: Int)

    /** Sends a notification now, or only one of its deliveries; its schedule is left alone. */
    @PostMapping("/api/notifications/{id}/send")
    fun send(user: NotifyUser, @PathVariable id: UUID, @RequestParam(required = false) deliveryId: UUID?): ResponseEntity<Any> =
        when (val result = dispatch.sendNow(id, user, deliveryId)) {
            is SendResult.Sent -> ResponseEntity.ok(
                result.reports.map {
                    DeliveryReportView(it.delivery.id, it.delivery.plugin, it.summary, it.outcome.delivered, it.outcome.detail, it.outcome.attempts)
                },
            )
            SendResult.NotFound -> ResponseEntity.notFound().build()
            SendResult.Forbidden -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            SendResult.NothingToSend -> ResponseEntity.unprocessableEntity().body(mapOf("message" to "no enabled delivery to send"))
        }

    @GetMapping("/api/dispatch")
    fun stats(user: NotifyUser): ResponseEntity<DispatchStats> =
        if (user.admin) ResponseEntity.ok(dispatch.stats()) else ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    /** Sends whatever is due without waiting for the next poll. */
    @PostMapping("/api/dispatch/run")
    fun run(user: NotifyUser): ResponseEntity<Map<String, Int>> =
        if (user.admin) ResponseEntity.ok(mapOf("dispatched" to dispatch.runDue())) else ResponseEntity.status(HttpStatus.FORBIDDEN).build()
}
