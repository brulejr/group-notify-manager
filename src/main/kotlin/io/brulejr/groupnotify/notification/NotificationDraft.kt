/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A notification as submitted, before it is checked: from the console's form or the
 * REST API. Times are local to [zone], as an author thinks of them.
 *
 * @property zone an IANA zone such as `America/New_York`; blank for the default.
 * @property runAt a one-off's time.
 * @property until a recurring schedule's last day, inclusive.
 */
data class NotificationDraft(
    val group: String,
    val title: String,
    val subject: String,
    val body: String,
    val format: BodyFormat,
    val kind: ScheduleKind,
    val zone: String = "",
    val runAt: LocalDateTime? = null,
    val cron: String = "",
    val until: LocalDate? = null,
    val deliveries: List<Delivery> = emptyList(),
    val paused: Boolean = false,
) {
    /**
     * @property id the stored delivery this replaces, so it keeps its id; null for a new one.
     * @property instance the id of one of the group's plugin instances.
     * @property overrides values for the plugin's overridable fields; any other is ignored.
     */
    data class Delivery(
        val id: String? = null,
        val instance: String,
        val overrides: Map<String, String> = emptyMap(),
        val enabled: Boolean = true,
    )
}
