/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.api

import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.instance.InstanceResult
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.PluginInstance
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
import java.util.UUID

/**
 * Plugin instances. Members of a group may list and read its instances, secrets masked,
 * to choose among them; only admins create, change or delete them. One outside the
 * caller's groups is not found.
 */
@RestController
@RequestMapping("/api/instances")
class InstanceController(
    private val instances: InstanceService,
    private val deliveries: DeliveryService,
) {

    @GetMapping
    fun list(user: NotifyUser, @RequestParam(required = false) group: String?): List<InstanceView> =
        instances.list(user, group).map(::view)

    @GetMapping("/{id}")
    fun get(user: NotifyUser, @PathVariable id: UUID): ResponseEntity<InstanceView> =
        instances.find(id, user)?.let { ResponseEntity.ok(view(it)) } ?: ResponseEntity.notFound().build()

    @PostMapping
    fun create(user: NotifyUser, @RequestBody request: InstanceRequest): ResponseEntity<Any> =
        respond(instances.create(request.toDraft(), user), HttpStatus.CREATED)

    @PutMapping("/{id}")
    fun update(user: NotifyUser, @PathVariable id: UUID, @RequestBody request: InstanceRequest): ResponseEntity<Any> =
        respond(instances.update(id, request.toDraft(), user))

    @DeleteMapping("/{id}")
    fun delete(user: NotifyUser, @PathVariable id: UUID): ResponseEntity<Any> = when (val result = instances.delete(id, user)) {
        is InstanceResult.Saved -> ResponseEntity.noContent().build()
        else -> respond(result)
    }

    private fun respond(result: InstanceResult, success: HttpStatus = HttpStatus.OK): ResponseEntity<Any> = when (result) {
        is InstanceResult.Saved -> ResponseEntity.status(success).body(view(result.instance))
        is InstanceResult.Invalid -> ResponseEntity.badRequest().body(InstanceErrors.of(result.problems))
        is InstanceResult.InUse -> ResponseEntity.status(HttpStatus.CONFLICT).body(InstanceErrors.inUse(result.notifications))
        InstanceResult.NotFound -> ResponseEntity.notFound().build()
        InstanceResult.Forbidden -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }

    private fun view(instance: PluginInstance) = InstanceView.of(instance, instances, deliveries.overridableKeys(instance.plugin))
}
