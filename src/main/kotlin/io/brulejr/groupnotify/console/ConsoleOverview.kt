/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.console

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.delivery.PluginDescriptor
import io.brulejr.groupnotify.notification.Notification

/** The dashboard, as one user may see it. Java collections, which templates can call methods on. */
data class ConsoleOverview(
    val active: Int,
    val paused: Int,
    val completed: Int,
    val sentLastDay: Long,
    val failedLastDay: Long,
    val upcoming: MutableList<Notification>,
    val recent: MutableList<AuditEvent>,
    val plugins: MutableList<PluginDescriptor>,
    val groups: MutableList<String>,
)
