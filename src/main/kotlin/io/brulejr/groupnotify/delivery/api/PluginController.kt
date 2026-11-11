/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery.api

import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.delivery.PluginDescriptor
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The installed delivery plugins: what each needs of a delivery, and whether it is ready. */
@RestController
@RequestMapping("/api/plugins")
class PluginController(private val deliveries: DeliveryService) {

    @GetMapping
    fun list(): List<PluginDescriptor> = deliveries.plugins()

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<PluginDescriptor> =
        deliveries.plugin(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}
