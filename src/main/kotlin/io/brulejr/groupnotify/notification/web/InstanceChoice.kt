/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification.web

import java.util.UUID

/** One of a group's instances, as the Add delivery menu offers it. */
data class InstanceChoice(
    val id: UUID,
    val name: String,
    val pluginName: String,
    val icon: String,
    val summary: String,
)
