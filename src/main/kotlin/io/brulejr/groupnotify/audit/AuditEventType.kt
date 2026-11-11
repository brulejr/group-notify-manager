/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

/** What happened to a notification. */
enum class AuditEventType(val label: String, val level: String) {
    CREATED("Created", "info"),
    UPDATED("Edited", "info"),
    PAUSED("Paused", "warning"),
    RESUMED("Resumed", "info"),
    DELETED("Deleted", "secondary"),

    /** One delivery was accepted by its service. */
    SENT("Sent", "success"),

    /** One delivery was not, after every attempt. */
    FAILED("Failed", "danger"),

    /** A run was missed by more than the misfire grace, so it was not sent late. */
    SKIPPED("Skipped", "warning"),

    /** A one-off has run, or a recurring one has passed its end date. */
    COMPLETED("Completed", "secondary"),
    ;

    val delivery get() = this == SENT || this == FAILED
}
