/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert.web

import io.brulejr.groupnotify.alert.AlertService
import io.brulejr.groupnotify.alert.AlertTestResult
import io.brulejr.groupnotify.console.web.Toast
import io.brulejr.groupnotify.security.NotifyUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID

/** The Delivery plugins page's Send test alert button. */
@Controller
@RequestMapping("/ui/alerts")
class AlertPageController(private val alerts: AlertService) {

    @PostMapping("/test/{instanceId}")
    fun test(user: NotifyUser, @PathVariable instanceId: UUID, model: Model): String {
        val toast = when (val r = alerts.test(instanceId, user)) {
            is AlertTestResult.Sent -> Toast.success("Test alert sent through ${r.instance}")
            is AlertTestResult.Failed -> Toast.danger("Test alert not sent through ${r.instance}: ${r.reason}")
            AlertTestResult.NotFound -> Toast.danger("No such delivery instance")
            AlertTestResult.Forbidden -> Toast.danger("You may not send alerts through this instance")
        }
        model.addAttribute("toast", toast)
        return "ui/layout :: toastOnly"
    }
}
