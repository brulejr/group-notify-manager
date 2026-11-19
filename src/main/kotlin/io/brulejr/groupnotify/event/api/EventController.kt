/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.api

import io.brulejr.groupnotify.event.EventResult
import io.brulejr.groupnotify.event.EventService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.api.NotificationErrors
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
import java.time.Instant
import java.util.UUID

/**
 * Events, limited to the caller's groups, whose members manage them. One in a group the
 * caller is not in is not found. Sending one now is `POST /api/events/{id}/send`.
 */
@RestController
@RequestMapping("/api/events")
class EventController(
    private val events: EventService,
    private val instances: InstanceService,
) {

    /** A note for one occurrence, which must be one of the event's, in Markdown or HTML; blank removes it. */
    data class NoteRequest(val occurrence: Instant? = null, val note: String = "", val format: BodyFormat = BodyFormat.MARKDOWN)

    /** A reminder still to be sent. */
    data class UpcomingView(val sendAt: Instant, val occurrence: Instant, val reminder: UUID, val describe: String?)

    @GetMapping
    fun list(user: NotifyUser, @RequestParam(required = false) group: String?, @RequestParam(required = false) q: String?): List<EventView> =
        events.list(user, group, q).map { EventView.of(it, instances) }

    @GetMapping("/{id}")
    fun get(user: NotifyUser, @PathVariable id: UUID): ResponseEntity<EventView> =
        events.find(id, user)?.let { ResponseEntity.ok(EventView.of(it, instances)) } ?: ResponseEntity.notFound().build()

    /** The next reminders it will send. */
    @GetMapping("/{id}/upcoming")
    fun upcoming(user: NotifyUser, @PathVariable id: UUID, @RequestParam(defaultValue = "5") count: Int): ResponseEntity<List<UpcomingView>> {
        val event = events.find(id, user) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(events.upcoming(event, count.coerceIn(1, 50)).map { UpcomingView(it.sendAt, it.occurrence, it.reminder, event.reminder(it.reminder)?.describe()) })
    }

    @PostMapping
    fun create(user: NotifyUser, @RequestBody request: EventRequest): ResponseEntity<Any> =
        respond(events.create(request.toDraft(), user), HttpStatus.CREATED)

    @PutMapping("/{id}")
    fun update(user: NotifyUser, @PathVariable id: UUID, @RequestBody request: EventRequest): ResponseEntity<Any> =
        respond(events.update(id, request.toDraft(), user))

    @PutMapping("/{id}/notes")
    fun note(user: NotifyUser, @PathVariable id: UUID, @RequestBody request: NoteRequest): ResponseEntity<Any> {
        val occurrence = request.occurrence ?: return ResponseEntity.badRequest().body(mapOf("message" to "occurrence is required"))
        return respond(events.setNote(id, occurrence, request.note, request.format, user))
    }

    @PostMapping("/{id}/pause")
    fun pause(user: NotifyUser, @PathVariable id: UUID) = respond(events.pause(id, user))

    @PostMapping("/{id}/resume")
    fun resume(user: NotifyUser, @PathVariable id: UUID) = respond(events.resume(id, user))

    @DeleteMapping("/{id}")
    fun delete(user: NotifyUser, @PathVariable id: UUID): ResponseEntity<Any> = when (val result = events.delete(id, user)) {
        is EventResult.Saved -> ResponseEntity.noContent().build()
        else -> respond(result)
    }

    private fun respond(result: EventResult, success: HttpStatus = HttpStatus.OK): ResponseEntity<Any> = when (result) {
        is EventResult.Saved -> ResponseEntity.status(success).body(EventView.of(result.event, instances))
        is EventResult.Invalid -> ResponseEntity.badRequest().body(NotificationErrors.of(result.problems))
        EventResult.NotFound -> ResponseEntity.notFound().build()
    }
}
