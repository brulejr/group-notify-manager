/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert.api

import io.brulejr.groupnotify.alert.AlertService
import io.brulejr.groupnotify.alert.AlertTestResult
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Test alerts, for those who manage the instance's group. */
@RestController
@RequestMapping("/api/alerts")
class AlertController(private val alerts: AlertService) {

    @PostMapping("/test/{instanceId}")
    fun test(user: NotifyUser, @PathVariable instanceId: UUID): ResponseEntity<AlertTestView> = when (val r = alerts.test(instanceId, user)) {
        is AlertTestResult.Sent -> ResponseEntity.ok(AlertTestView(true, r.instance, r.detail))
        is AlertTestResult.Failed -> ResponseEntity.ok(AlertTestView(false, r.instance, r.reason))
        AlertTestResult.NotFound -> ResponseEntity.notFound().build()
        AlertTestResult.Forbidden -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
}
