/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.web

import java.time.Instant

/**
 * One coming occurrence on an event's page: when, its note as it reads, and the
 * reminders it will be sent, e.g. `1 day before: Mon 16 Nov, 7:00 PM`.
 *
 * @property key the occurrence in epoch seconds, as its note's page is addressed.
 * @property noteHtml its note, sanitised; empty when it has none.
 */
data class OccurrenceRow(val occurrence: Instant, val key: Long, val noteHtml: String, val reminders: MutableList<String>) {
    val hasNote get() = noteHtml.isNotBlank()
}
