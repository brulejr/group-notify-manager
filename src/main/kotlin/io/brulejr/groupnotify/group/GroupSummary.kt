/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.group

import java.time.Instant

/**
 * A group as the console and API list it.
 *
 * @property member whether the viewer is in it; an admin also sees groups they are not in.
 */
data class GroupSummary(
    val name: String,
    val displayName: String,
    val member: Boolean,
    val canEdit: Boolean,
    val active: Int,
    val paused: Int,
    val total: Int,
    val nextRunAt: Instant?,
)
