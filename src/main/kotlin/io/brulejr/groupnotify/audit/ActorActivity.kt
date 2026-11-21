/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

import java.time.Instant

/** What one person did, from the audit trail: how many events they caused, and the latest. */
data class ActorActivity(val actor: String, val events: Long, val lastAt: Instant)
