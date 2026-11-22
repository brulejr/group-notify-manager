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

    data class DeliveryReportView(val deliveryId: UUID, val instance: UUID, val instanceName: String, val summary: String, val delivered: Boolean, val detail: String, val attempts: Int)

    /** Sends a notification now, or only one of its deliveries; its schedule is left alone. */
    @PostMapping("/api/messages/{id}/send")
    fun send(user: NotifyUser, @PathVariable id: UUID, @RequestParam(required = false) deliveryId: UUID?): ResponseEntity<Any> =
        respond(dispatch.sendNow(id, user, deliveryId))

    /**
     * Sends one of an event's reminders now, for its next occurrence, through its
     * deliveries or only one of them; its schedule is left alone.
     */
    @PostMapping("/api/events/{id}/send")
    fun sendEvent(
        user: NotifyUser,
        @PathVariable id: UUID,
        @RequestParam reminderId: UUID,
        @RequestParam(required = false) deliveryId: UUID?,
    ): ResponseEntity<Any> = respond(dispatch.sendEventNow(id, user, reminderId, deliveryId))

    private fun respond(result: SendResult): ResponseEntity<Any> =
        when (result) {
            is SendResult.Sent -> ResponseEntity.ok(
                result.reports.map {
                    DeliveryReportView(it.delivery.id, it.delivery.instance, it.name, it.summary, it.outcome.delivered, it.outcome.detail, it.outcome.attempts)
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
