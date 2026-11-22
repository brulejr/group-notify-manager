/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

/**
 * Told of every event once it is recorded, such as the alerts feature, which depends on
 * this one rather than the other way round. A listener that throws is logged and the
 * event stays recorded.
 */
fun interface AuditListener {
    fun recorded(event: AuditEvent)
}
